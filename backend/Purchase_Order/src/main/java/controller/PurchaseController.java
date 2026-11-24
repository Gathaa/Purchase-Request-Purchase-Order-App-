package controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import Ninova.dto.PageResponse;
import Ninova.dto.PoResponse;
import Ninova.dto.RequestInput;
import Ninova.dto.RequestResponse;
import Ninova.dto.StatusChangeInput;
import Ninova.enums.Department;
import Ninova.enums.RequestStatus;
import Service.PurchaseService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class PurchaseController {

    private final PurchaseService service;

    public PurchaseController(PurchaseService service) {
        this.service = service;
    }

    // ---------- purchase requests ----------

    @PostMapping("/purchase-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public RequestResponse create(@Valid @RequestBody RequestInput dto) {
        return service.create(dto);
    }

    @GetMapping("/purchase-requests/{id}")
    public RequestResponse getRequest(@PathVariable Long id) {
        return service.getRequest(id);
    }

    // Filters: status, department, date range (from/to, yyyy-MM-dd) on created or needed date.
    // Sort: direction asc/desc on that same date. Default: created date, newest first.
    @GetMapping("/purchase-requests")
    public PageResponse<RequestResponse> listRequests(
            @RequestParam(required = false) RequestStatus status,
            @RequestParam(required = false) Department department,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String dateField,
            @RequestParam(required = false) String direction,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return service.listRequests(status, department, from, to, dateField, direction, page, size);
    }

    @PutMapping("/purchase-requests/{id}")
    public RequestResponse update(@PathVariable Long id, @Valid @RequestBody RequestInput dto) {
        return service.update(id, dto);
    }

    // Every status change, including Approved -> Ordered (which creates the PO)
    @PostMapping("/purchase-requests/{id}/status")
    public RequestResponse changeStatus(@PathVariable Long id, @Valid @RequestBody StatusChangeInput dto) {
        return service.changeStatus(id, dto.newStatus(), dto.comment(), dto.version());
    }

    // The PO that was created from a request
    @GetMapping("/purchase-requests/{id}/purchase-order")
    public PoResponse getOrderByRequest(@PathVariable Long id) {
        return service.getOrderByRequest(id);
    }

    // ---------- purchase orders ----------

    // Filters: status (Draft PO / Issued), PO date range. Sort: direction on the PO date.
    @GetMapping("/purchase-orders")
    public PageResponse<PoResponse> listOrders(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String direction,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return service.listOrders(status, from, to, direction, page, size);
    }

    // Also the "PO data for the PDF"
    @GetMapping("/purchase-orders/{id}")
    public PoResponse getOrder(@PathVariable Long id) {
        return service.getOrder(id);
    }

    @PostMapping("/purchase-orders/{id}/issue")
    public PoResponse issueOrder(@PathVariable Long id) {
        return service.issueOrder(id);
    }
}