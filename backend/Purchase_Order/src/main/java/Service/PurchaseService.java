package Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import Ninova.GlobalExceptionHandler.BusinessRuleException;
import Ninova.GlobalExceptionHandler.ConflictException;
import Ninova.GlobalExceptionHandler.ResourceNotFoundException;
import Ninova.dto.ItemInput;
import Ninova.dto.ItemResponse;
import Ninova.dto.PageResponse;
import Ninova.dto.PoResponse;
import Ninova.dto.RequestInput;
import Ninova.dto.RequestResponse;
import Ninova.dto.RequestResponse.HistoryResponse;
import Ninova.entity.PoSequence;
import Ninova.entity.PurchaseOrder;
import Ninova.entity.PurchaseOrderItem;
import Ninova.entity.PurchaseRequest;
import Ninova.entity.PurchaseRequestItem;
import Ninova.enums.Department;
import Ninova.enums.RequestStatus;
import Repository.PoSequenceRepository;
import Repository.PurchaseOrderRepository;
import Repository.PurchaseRequestRepository;

@Service
public class PurchaseService {

    private static final ZoneId QATAR = ZoneId.of("Asia/Qatar");
    private static final BigDecimal MAX_TOTAL = new BigDecimal("50000");

    // The only allowed status changes (Section 2 of the task)
    private static final Map<RequestStatus, Set<RequestStatus>> ALLOWED = Map.of(
            RequestStatus.Draft, Set.of(RequestStatus.Submitted, RequestStatus.Cancelled),
            RequestStatus.Submitted, Set.of(RequestStatus.Approved, RequestStatus.Rejected, RequestStatus.Draft),
            RequestStatus.Approved, Set.of(RequestStatus.Ordered),
            RequestStatus.Rejected, Set.of(),
            RequestStatus.Cancelled, Set.of(),
            RequestStatus.Ordered, Set.of());

    private final PurchaseRequestRepository requestRepository;
    private final PurchaseOrderRepository orderRepository;
    private final PoSequenceRepository sequenceRepository;

    public PurchaseService(PurchaseRequestRepository requestRepository,
                           PurchaseOrderRepository orderRepository,
                           PoSequenceRepository sequenceRepository) {
        this.requestRepository = requestRepository;
        this.orderRepository = orderRepository;
        this.sequenceRepository = sequenceRepository;
    }

    // =====================  PURCHASE REQUESTS  =====================

    @Transactional
    public RequestResponse create(RequestInput dto) {
        checkBusinessRules(dto);
        PurchaseRequest request = new PurchaseRequest();
        applyData(request, dto);
        request.addHistory(null, RequestStatus.Draft, "Request created");
        request = requestRepository.save(request);
        return toResponse(request, true);
    }

    @Transactional(readOnly = true)
    public RequestResponse getRequest(Long id) {
        return toResponse(findRequest(id), true);
    }

    @Transactional(readOnly = true)
    public PageResponse<RequestResponse> listRequests(RequestStatus status, Department department,
                                                      LocalDate from, LocalDate to,
                                                      String dateField, String direction,
                                                      int page, int size) {
        checkPaging(page, size);
        checkRange(from, to);

        // dateField decides which date the range filter AND the sorting use
        boolean needed;
        if (dateField == null || dateField.isBlank() || dateField.equalsIgnoreCase("created")) {
            needed = false;
        } else if (dateField.equalsIgnoreCase("needed")) {
            needed = true;
        } else {
            throw new BusinessRuleException("dateField must be 'created' or 'needed'");
        }

        LocalDateTime createdFrom = null;
        LocalDateTime createdTo = null;
        LocalDate neededFrom = null;
        LocalDate neededTo = null;
        if (needed) {
            neededFrom = from;
            neededTo = to;
        } else {
            createdFrom = from == null ? null : from.atStartOfDay();
            createdTo = to == null ? null : to.plusDays(1).atStartOfDay(); // end date included
        }

        Sort sort = Sort.by(parseDirection(direction), needed ? "neededByDate" : "createdAt", "id");
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<PurchaseRequest> result = requestRepository.search(status, department,
                createdFrom, createdTo, neededFrom, neededTo, pageable);

        List<RequestResponse> content = result.getContent().stream()
                .map(r -> toResponse(r, false)).toList();
        return new PageResponse<>(content, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    // Draft only
    @Transactional
    public RequestResponse update(Long id, RequestInput dto) {
        PurchaseRequest request = findRequest(id);

        if (request.getStatus() != RequestStatus.Draft) {
            throw new ConflictException("Only requests in Draft can be edited. Current status: "
                    + request.getStatus());
        }
        if (dto.version() == null) {
            throw new BusinessRuleException("version is required when updating a request");
        }
        if (!dto.version().equals(request.getVersion())) {
            throw new ConflictException("The request was changed since you loaded it. Reload it and try again.");
        }

        checkBusinessRules(dto);
        applyData(request, dto);
        requestRepository.saveAndFlush(request);
        return toResponse(request, true);
    }

    // One transaction: status + history (+ the purchase order when going to Ordered).
    // If anything fails, nothing is saved.
    @Transactional
    public RequestResponse changeStatus(Long id, RequestStatus newStatus, String comment, Long version) {
        PurchaseRequest r = findRequest(id);

        if (comment == null || comment.trim().length() < 5) {
            throw new BusinessRuleException("Comment must be at least 5 characters");
        }
        if (version != null && !version.equals(r.getVersion())) {
            throw new ConflictException("The request was changed since you loaded it. Reload it and try again.");
        }

        RequestStatus current = r.getStatus();
        if (!ALLOWED.get(current).contains(newStatus)) {
            throw new ConflictException("Status change not allowed: " + current + " -> " + newStatus);
        }

        if (current == RequestStatus.Draft && newStatus == RequestStatus.Submitted) {
            validateForSubmit(r);
        }
        if (newStatus == RequestStatus.Ordered) {
            createOrderFromRequest(r);
        }

        r.setStatus(newStatus);
        r.setUpdatedAt(LocalDateTime.now(QATAR));
        r.addHistory(current, newStatus, comment.trim());
        requestRepository.saveAndFlush(r);
        return toResponse(r, true);
    }

    // =====================  PURCHASE ORDERS  =====================

    @Transactional(readOnly = true)
    public PageResponse<PoResponse> listOrders(String status, LocalDate from, LocalDate to,
                                               String direction, int page, int size) {
        checkPaging(page, size);
        checkRange(from, to);

        String statusFilter = (status == null || status.isBlank()) ? null : status.trim();
        if (statusFilter != null && !statusFilter.equals(PurchaseOrder.DRAFT)
                && !statusFilter.equals(PurchaseOrder.ISSUED)) {
            throw new BusinessRuleException("status must be 'Draft PO' or 'Issued'");
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by(parseDirection(direction), "poDate", "id"));
        Page<PurchaseOrder> result = orderRepository.search(statusFilter, from, to, pageable);

        List<PoResponse> content = result.getContent().stream().map(p -> toResponse(p, false)).toList();
        return new PageResponse<>(content, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    // Also the "PO data for the PDF"
    @Transactional(readOnly = true)
    public PoResponse getOrder(Long id) {
        return toResponse(findOrder(id), true);
    }

    @Transactional(readOnly = true)
    public PoResponse getOrderByRequest(Long requestId) {
        PurchaseOrder po = orderRepository.findByPurchaseRequestId(requestId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No purchase order found for request " + requestId));
        return toResponse(po, true);
    }

    // Draft PO -> Issued, only once
    @Transactional
    public PoResponse issueOrder(Long id) {
        findOrder(id); // 404 if it does not exist
        int updated = orderRepository.markIssued(id, PurchaseOrder.ISSUED, PurchaseOrder.DRAFT,
                LocalDateTime.now(QATAR));
        if (updated == 0) {
            throw new ConflictException("Purchase order is already issued");
        }
        return toResponse(findOrder(id), true);
    }

    // Runs inside changeStatus's transaction: if it fails, the status change rolls back too
    private void createOrderFromRequest(PurchaseRequest r) {
        if (orderRepository.existsByPurchaseRequestId(r.getId())) {
            throw new ConflictException("This request already has a purchase order");
        }
        LocalDate today = LocalDate.now(QATAR);

        PurchaseOrder po = new PurchaseOrder();
        po.setPurchaseRequestId(r.getId());
        po.setPoNumber(nextPoNumber(today.getYear()));
        po.setPoDate(today);
        po.setSupplierName(r.getSupplierName());
        po.setRequesterName(r.getRequesterName());
        po.setDepartment(r.getDepartment().name());
        po.setNeededByDate(r.getNeededByDate());

        // copy the items and recalculate the totals on the backend
        BigDecimal total = BigDecimal.ZERO;
        for (PurchaseRequestItem i : r.getItems()) {
            PurchaseOrderItem item = new PurchaseOrderItem();
            item.setPurchaseOrder(po);
            item.setName(i.getName());
            item.setQuantity(i.getQuantity());
            item.setUnitPrice(i.getUnitPrice());
            BigDecimal line = i.getUnitPrice().multiply(BigDecimal.valueOf(i.getQuantity()))
                    .setScale(2, RoundingMode.HALF_UP);
            item.setLineTotal(line);
            po.getItems().add(item);
            total = total.add(line);
        }
        po.setGrandTotal(total);

        orderRepository.saveAndFlush(po);
    }

    // PO-2026-0001, restarts every year. The row lock makes it safe for simultaneous requests.
    private String nextPoNumber(int year) {
        sequenceRepository.ensureRow(year);
        PoSequence seq = sequenceRepository.lockByYear(year)
                .orElseThrow(() -> new IllegalStateException("PO sequence row missing"));
        int next = seq.getLastValue() + 1;
        seq.setLastValue(next);
        sequenceRepository.save(seq);
        return String.format("PO-%d-%04d", year, next);
    }

    // =====================  HELPERS  =====================

    private PurchaseRequest findRequest(Long id) {
        return requestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase request " + id + " not found"));
    }

    private PurchaseOrder findOrder(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase order " + id + " not found"));
    }

    private void checkPaging(int page, int size) {
        if (page < 0) throw new BusinessRuleException("page must be 0 or more");
        if (size < 1 || size > 100) throw new BusinessRuleException("size must be between 1 and 100");
    }

    private void checkRange(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new BusinessRuleException("'From' date must not be after 'To' date");
        }
    }

    // default is newest first
    private Sort.Direction parseDirection(String direction) {
        if (direction == null || direction.isBlank() || direction.equalsIgnoreCase("desc")) {
            return Sort.Direction.DESC;
        }
        if (direction.equalsIgnoreCase("asc")) return Sort.Direction.ASC;
        throw new BusinessRuleException("direction must be 'asc' or 'desc'");
    }

    private void checkBusinessRules(RequestInput dto) {
        if (dto.neededByDate().isBefore(LocalDate.now(QATAR))) {
            throw new BusinessRuleException("Needed-by date must be today or a future date");
        }
        if (dto.items() == null || dto.items().isEmpty() || dto.items().size() > 20) {
            throw new BusinessRuleException("A request must have 1 to 20 items");
        }
    }

    // Draft -> Submitted: re-checks every rule on the saved data, plus the QAR 50,000 limit
    private void validateForSubmit(PurchaseRequest r) {
        Set<String> problems = new LinkedHashSet<>();

        int nameLen = r.getRequesterName() == null ? 0 : r.getRequesterName().trim().length();
        if (nameLen < 3 || nameLen > 80) problems.add("Requester name must be 3 to 80 characters");
        if (r.getDepartment() == null) problems.add("Department is required");
        if (r.getNeededByDate() == null || r.getNeededByDate().isBefore(LocalDate.now(QATAR))) {
            problems.add("Needed-by date must be today or a future date");
        }
        int supplierLen = r.getSupplierName() == null ? 0 : r.getSupplierName().trim().length();
        if (supplierLen < 1 || supplierLen > 150) problems.add("Supplier name is required (max 150 characters)");
        int justLen = r.getJustification() == null ? 0 : r.getJustification().trim().length();
        if (justLen < 10 || justLen > 1000) problems.add("Justification must be 10 to 1000 characters");

        List<PurchaseRequestItem> items = r.getItems();
        if (items.isEmpty() || items.size() > 20) problems.add("A request must have 1 to 20 items");

        BigDecimal total = BigDecimal.ZERO;
        for (PurchaseRequestItem i : items) {
            if (i.getName() == null || i.getName().isBlank() || i.getName().length() > 200) {
                problems.add("Every item needs a name (max 200 characters)");
            }
            if (i.getQuantity() == null || i.getQuantity() < 1 || i.getQuantity() > 999) {
                problems.add("Item quantity must be 1 to 999");
            }
            if (i.getUnitPrice() == null || i.getUnitPrice().signum() <= 0) {
                problems.add("Item unit price must be more than 0");
            } else if (i.getQuantity() != null) {
                total = total.add(i.getUnitPrice().multiply(BigDecimal.valueOf(i.getQuantity())));
            }
        }
        if (total.compareTo(MAX_TOTAL) > 0) {
            problems.add("Grand total must not be more than QAR 50,000 (it is QAR "
                    + total.setScale(2, RoundingMode.HALF_UP) + ")");
        }

        if (!problems.isEmpty()) {
            throw new BusinessRuleException("Cannot submit: " + String.join("; ", problems));
        }
    }

    // Copies the fields and calculates line totals and grand total (never trusts the client)
    private void applyData(PurchaseRequest request, RequestInput dto) {
        request.setRequesterName(dto.requesterName());
        request.setDepartment(dto.department());
        request.setNeededByDate(dto.neededByDate());
        request.setSupplierName(dto.supplierName());
        request.setJustification(dto.justification());

        request.getItems().clear();
        BigDecimal total = BigDecimal.ZERO;
        for (ItemInput i : dto.items()) {
            PurchaseRequestItem item = new PurchaseRequestItem();
            item.setPurchaseRequest(request);
            item.setName(i.name());
            item.setQuantity(i.quantity());
            BigDecimal unitPrice = i.unitPrice().setScale(2, RoundingMode.HALF_UP);
            item.setUnitPrice(unitPrice);
            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(i.quantity()))
                    .setScale(2, RoundingMode.HALF_UP);
            item.setLineTotal(lineTotal);
            request.getItems().add(item);
            total = total.add(lineTotal);
        }
        request.setGrandTotal(total);
        request.setUpdatedAt(LocalDateTime.now(QATAR)); // marks the row as changed, so the version increases
    }

    private RequestResponse toResponse(PurchaseRequest r, boolean withHistory) {
        List<ItemResponse> items = r.getItems().stream()
                .map(i -> new ItemResponse(i.getId(), i.getName(), i.getQuantity(),
                        i.getUnitPrice(), i.getLineTotal()))
                .toList();

        List<HistoryResponse> history = null;
        if (withHistory) {
            history = r.getHistory().stream()
                    .map(h -> new HistoryResponse(h.getId(), h.getFromStatus(), h.getToStatus(),
                            h.getComments(), h.getChangedAt()))
                    .toList();
        }
        return new RequestResponse(r.getId(), r.getRequesterName(), r.getDepartment(),
                r.getNeededByDate(), r.getSupplierName(), r.getJustification(), r.getStatus(),
                r.getGrandTotal(), r.getVersion(), r.getCreatedAt(), r.getUpdatedAt(), items, history);
    }

    private PoResponse toResponse(PurchaseOrder po, boolean withItems) {
        List<ItemResponse> items = null;
        if (withItems) {
            items = po.getItems().stream()
                    .map(i -> new ItemResponse(i.getId(), i.getName(), i.getQuantity(),
                            i.getUnitPrice(), i.getLineTotal()))
                    .toList();
        }
        return new PoResponse(po.getId(), po.getPurchaseRequestId(), po.getPoNumber(), po.getPoDate(),
                po.getSupplierName(), po.getRequesterName(), po.getDepartment(), po.getNeededByDate(),
                po.getGrandTotal(), po.getStatus(), po.getIssuedAt(), po.getCreatedAt(), items);
    }
}