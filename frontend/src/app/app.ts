import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';

const API = 'http://localhost:8080/api/purchase-requests';
const PO_API = 'http://localhost:8080/api/purchase-orders';

interface Item { id?: number; name: string; quantity: number | null; unitPrice: number | null; lineTotal?: number; }
interface HistoryEntry { id: number; fromStatus: string | null; toStatus: string; comments: string; changedAt: string; }
interface PurchaseRequest {
  id: number; requesterName: string; department: string; neededByDate: string; supplierName: string;
  justification: string; status: string; grandTotal: number; version: number; createdAt: string;
  items: Item[]; history?: HistoryEntry[];
}
interface PurchaseOrder {
  id: number; purchaseRequestId: number; poNumber: string; poDate: string; supplierName: string;
  requesterName: string; department: string; neededByDate: string; grandTotal: number;
  status: string; issuedAt?: string; createdAt: string; items?: Item[];
}
interface Page<T> { content: T[]; page: number; size: number; totalElements: number; totalPages: number; }

@Component({
  selector: 'app-root',
  imports: [FormsModule, DecimalPipe, DatePipe],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App implements OnInit {
  private sanitizer = inject(DomSanitizer);

  departments = ['IT', 'Operations', 'Finance', 'HR'];
  statuses = ['Draft', 'Submitted', 'Approved', 'Rejected', 'Cancelled', 'Ordered'];
  poStatuses = ['Draft PO', 'Issued'];

  // Which buttons to show for each status (the backend enforces the same rules)
  transitions: Record<string, string[]> = {
    Draft: ['Submitted', 'Cancelled'],
    Submitted: ['Approved', 'Rejected', 'Draft'],
    Approved: ['Ordered'],
    Rejected: [],
    Cancelled: [],
    Ordered: []
  };
  labels: Record<string, string> = {
    Submitted: 'Submit', Cancelled: 'Cancel request', Approved: 'Approve',
    Rejected: 'Reject', Draft: 'Return to Draft', Ordered: 'Convert to PO'
  };

  view = signal<'list' | 'form' | 'detail' | 'poList' | 'poDetail'>('list');
  error = signal('');
  saving = signal(false);
  busy = signal(false);

  // request list: filters, date range and sorting (all handled by the backend)
  data = signal<Page<PurchaseRequest> | null>(null);
  page = 0;
  size = 10;
  filterStatus = '';
  filterDepartment = '';
  dateField = 'created';   // which date the range and the sorting use: created | needed
  filterFrom = '';
  filterTo = '';
  direction = 'desc';      // desc = newest first, asc = oldest first

  // request detail + status change
  current = signal<PurchaseRequest | null>(null);
  linkedPo = signal<PurchaseOrder | null>(null);
  pending = signal<string | null>(null);
  comment = '';

  // PO list + detail
  poData = signal<Page<PurchaseOrder> | null>(null);
  poPage = 0;
  poStatus = '';
  poFrom = '';
  poTo = '';
  poDirection = 'desc';
  currentPo = signal<PurchaseOrder | null>(null);

  // inline PDF viewer
  pdfUrl = signal<SafeResourceUrl | null>(null);
  pdfTitle = signal('');
  private pdfObjectUrl: string | null = null;

  // form
  editId: number | null = null;
  version: number | null = null;
  model = this.emptyModel();

  ngOnInit() { this.loadList(); }

  // ---------- HTTP helpers: return the body or throw the backend's error message ----------
  private async call<T>(url: string, init?: RequestInit): Promise<T> {
    let res: Response;
    try {
      res = await fetch(url, init);
    } catch {
      throw new Error('Cannot reach the server. Is the backend running?');
    }
    const body = await res.json().catch(() => null);
    if (!res.ok) throw new Error(body?.message ?? 'Error ' + res.status);
    return body as T;
  }

  private post<T>(url: string, body?: unknown): Promise<T> {
    return this.call<T>(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: body === undefined ? undefined : JSON.stringify(body)
    });
  }

  badgeClass(status: string): string { return 'badge ' + status.replace(/\s+/g, ''); }
  label(status: string): string { return this.labels[status] ?? status; }

  // ---------- request list ----------
  async loadList() {
    const p = new URLSearchParams({
      page: String(this.page),
      size: String(this.size),
      dateField: this.dateField,
      direction: this.direction
    });
    if (this.filterStatus) p.set('status', this.filterStatus);
    if (this.filterDepartment) p.set('department', this.filterDepartment);
    if (this.filterFrom) p.set('from', this.filterFrom);
    if (this.filterTo) p.set('to', this.filterTo);
    try {
      this.data.set(await this.call<Page<PurchaseRequest>>(API + '?' + p.toString()));
      this.error.set('');
    } catch (e: any) {
      this.error.set(e.message);
    }
  }

  showList() { this.error.set(''); this.view.set('list'); this.loadList(); }
  filter() { this.page = 0; this.loadList(); }
  go(p: number) { this.page = p; this.loadList(); }

  clearFilters() {
    this.filterStatus = '';
    this.filterDepartment = '';
    this.dateField = 'created';
    this.filterFrom = '';
    this.filterTo = '';
    this.direction = 'desc';
    this.filter();
  }

  // ---------- request detail ----------
  async showDetail(id: number) {
    try {
      const r = await this.call<PurchaseRequest>(API + '/' + id);
      this.current.set(r);
      this.linkedPo.set(null);
      this.cancelChange();
      this.error.set('');
      this.view.set('detail');
      if (r.status === 'Ordered') {
        try {
          this.linkedPo.set(await this.call<PurchaseOrder>(API + '/' + id + '/purchase-order'));
        } catch { /* the PO link is optional */ }
      }
    } catch (e: any) {
      this.error.set(e.message);
    }
  }

  private async reloadCurrent(id: number) {
    try {
      this.current.set(await this.call<PurchaseRequest>(API + '/' + id));
    } catch { /* keep the error that is already shown */ }
  }

  // ---------- status change ----------
  startChange(status: string) { this.pending.set(status); this.comment = ''; this.error.set(''); }
  cancelChange() { this.pending.set(null); this.comment = ''; }

  async confirmChange() {
    const r = this.current();
    const status = this.pending();
    if (!r || !status || this.busy()) return;

    this.busy.set(true);
    try {
      // every change, including "Ordered", goes through the same endpoint
      const updated = await this.post<PurchaseRequest>(API + '/' + r.id + '/status',
        { newStatus: status, comment: this.comment, version: r.version });
      this.cancelChange();

      if (status === 'Ordered') {
        // the backend created the purchase order in the same transaction: open it
        const po = await this.call<PurchaseOrder>(API + '/' + r.id + '/purchase-order');
        await this.showPoDetail(po.id);
      } else {
        this.current.set(updated);
        this.error.set('');
      }
    } catch (e: any) {
      this.error.set(e.message);
      await this.reloadCurrent(r.id); // show the real state, buttons update
      const cur = this.current();
      if (cur && !(this.transitions[cur.status] ?? []).includes(status)) this.cancelChange();
    } finally {
      this.busy.set(false);
    }
  }

  // ---------- form ----------
  emptyModel() {
    return {
      requesterName: '', department: '', neededByDate: '', supplierName: '', justification: '',
      items: [{ name: '', quantity: 1, unitPrice: null } as Item]
    };
  }

  newRequest() {
    this.editId = null;
    this.version = null;
    this.model = this.emptyModel();
    this.error.set('');
    this.view.set('form');
  }

  editRequest(r: PurchaseRequest) {
    this.editId = r.id;
    this.version = r.version;
    this.model = {
      requesterName: r.requesterName, department: r.department, neededByDate: r.neededByDate,
      supplierName: r.supplierName, justification: r.justification,
      items: r.items.map(i => ({ name: i.name, quantity: i.quantity, unitPrice: i.unitPrice }))
    };
    this.error.set('');
    this.view.set('form');
  }

  addItem() { this.model.items.push({ name: '', quantity: 1, unitPrice: null }); }
  removeItem(i: number) { this.model.items.splice(i, 1); }
  line(it: Item): number { return (it.quantity ?? 0) * (it.unitPrice ?? 0); }
  estimate(): number { return this.model.items.reduce((s, it) => s + this.line(it), 0); }

  async save() {
    if (this.saving()) return;
    const m = this.model;
    // No client-side blocking on purpose: the backend validates and we show its message
    const body = {
      requesterName: m.requesterName,
      department: m.department || null,
      neededByDate: m.neededByDate || null,
      supplierName: m.supplierName,
      justification: m.justification,
      items: m.items.map(i => ({ name: i.name, quantity: i.quantity, unitPrice: i.unitPrice })),
      version: this.editId ? this.version : null
    };

    this.saving.set(true);
    try {
      const saved = await this.call<PurchaseRequest>(this.editId ? API + '/' + this.editId : API, {
        method: this.editId ? 'PUT' : 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body)
      });
      this.current.set(saved);
      this.linkedPo.set(null);
      this.cancelChange();
      this.error.set('');
      this.view.set('detail');
    } catch (e: any) {
      this.error.set(e.message);
    } finally {
      this.saving.set(false);
    }
  }

  // ---------- purchase orders ----------
  async loadPoList() {
    const p = new URLSearchParams({
      page: String(this.poPage),
      size: String(this.size),
      direction: this.poDirection
    });
    if (this.poStatus) p.set('status', this.poStatus);
    if (this.poFrom) p.set('from', this.poFrom);
    if (this.poTo) p.set('to', this.poTo);
    try {
      this.poData.set(await this.call<Page<PurchaseOrder>>(PO_API + '?' + p.toString()));
      this.error.set('');
    } catch (e: any) {
      this.error.set(e.message);
    }
  }

  showPoList() { this.error.set(''); this.view.set('poList'); this.loadPoList(); }
  poFilter() { this.poPage = 0; this.loadPoList(); }
  goPo(p: number) { this.poPage = p; this.loadPoList(); }

  clearPoFilters() {
    this.poStatus = '';
    this.poFrom = '';
    this.poTo = '';
    this.poDirection = 'desc';
    this.poFilter();
  }

  async showPoDetail(id: number) {
    try {
      this.currentPo.set(await this.call<PurchaseOrder>(PO_API + '/' + id));
      this.error.set('');
      this.view.set('poDetail');
    } catch (e: any) {
      this.error.set(e.message);
    }
  }

  async issuePo() {
    const po = this.currentPo();
    if (!po || this.busy()) return;
    this.busy.set(true);
    try {
      this.currentPo.set(await this.post<PurchaseOrder>(PO_API + '/' + po.id + '/issue'));
      this.error.set('');
    } catch (e: any) {
      this.error.set(e.message);
      try { this.currentPo.set(await this.call<PurchaseOrder>(PO_API + '/' + po.id)); } catch { }
    } finally {
      this.busy.set(false);
    }
  }

  // ---------- PDF (pdfmake, built in the browser from the PO data of the backend) ----------

  // Header and footer images, drawn on a canvas (any image is allowed by the task)
  private banner(w: number, h: number, title: string, sub: string): string {
    const c = document.createElement('canvas');
    c.width = w;
    c.height = h;
    const g = c.getContext('2d')!;
    g.fillStyle = '#1f2a44';
    g.fillRect(0, 0, w, h);
    g.fillStyle = '#2f5bea';
    g.fillRect(0, h - 10, w, 10);
    g.fillStyle = '#ffffff';
    g.textBaseline = 'middle';
    g.font = 'bold ' + Math.round(h * 0.32) + 'px Arial';
    g.fillText(title, 40, sub ? h * 0.40 : h * 0.45);
    if (sub) {
      g.font = Math.round(h * 0.17) + 'px Arial';
      g.fillStyle = '#cfd6e6';
      g.fillText(sub, 40, h * 0.72);
    }
    return c.toDataURL('image/png');
  }

  // Works with both the callback style (pdfmake 0.2) and the promise style (pdfmake 0.3)
  private getBlob(pdf: any): Promise<Blob> {
    return new Promise<Blob>((resolve, reject) => {
      try {
        const result = pdf.getBlob((b: Blob) => resolve(b));
        if (result && typeof result.then === 'function') result.then(resolve, reject);
      } catch (e) {
        reject(e);
      }
    });
  }

  // Loads fresh PO data from the backend and builds the pdfmake document
  private async buildPdf(poId: number): Promise<{ pdf: any; po: PurchaseOrder }> {
    const po = await this.call<PurchaseOrder>(PO_API + '/' + poId);
    if (this.view() === 'poDetail') this.currentPo.set(po);

    let pdfMake: any;
    try {
      // @ts-ignore - pdfmake ships as CommonJS, loaded only when a PDF is requested
      const pdfMakeModule: any = await import('pdfmake/build/pdfmake');
      // @ts-ignore
      const fontsModule: any = await import('pdfmake/build/vfs_fonts');
      pdfMake = pdfMakeModule.default ?? pdfMakeModule;
      const fonts = fontsModule.default ?? fontsModule;
      const vfs = fonts.pdfMake?.vfs ?? fonts.vfs ?? fonts;
      if (typeof pdfMake.addVirtualFileSystem === 'function') pdfMake.addVirtualFileSystem(vfs);
      else pdfMake.vfs = vfs;
    } catch (e: any) {
      throw new Error('The PDF library could not be loaded: ' + (e?.message ?? e));
    }

    const money = (n: number | null | undefined) =>
      (n ?? 0).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    const stamp = (s: string) => new Date(s).toLocaleString('en-GB',
      { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' });
    const kv = (k: string, v: string) =>
      ({ text: [{ text: k + ': ', bold: true }, v], margin: [0, 2, 0, 2] });

    const headerImg = this.banner(1190, 140, 'NINOVA TECHNOLOGY', 'Procurement Department');
    const footerImg = this.banner(1190, 80, 'Doha, Qatar  |  procurement@ninova.example', '');

    const content: any[] = [
      { text: 'PURCHASE ORDER', style: 'title' },
      { text: po.poNumber, style: 'poNumber' },
      {
        columns: [
          { width: '*', stack: [kv('PO date', po.poDate), kv('Supplier', po.supplierName), kv('Requester', po.requesterName)] },
          { width: '*', stack: [kv('Department', po.department), kv('Needed by', po.neededByDate)] }
        ],
        margin: [0, 10, 0, 6]
      }
    ];

    // "Issued on <date>" only when the PO is issued
    if (po.issuedAt) {
      content.push({ text: 'Issued on ' + stamp(po.issuedAt), italics: true, margin: [0, 4, 0, 12] });
    } else {
      content.push({ text: '', margin: [0, 0, 0, 12] });
    }

    content.push({
      table: {
        headerRows: 1,
        widths: ['*', 60, 90, 95],
        body: [
          [
            { text: 'Name', style: 'th' },
            { text: 'Quantity', style: 'th', alignment: 'right' },
            { text: 'Unit price (QAR)', style: 'th', alignment: 'right' },
            { text: 'Line total (QAR)', style: 'th', alignment: 'right' }
          ],
          ...(po.items ?? []).map(i => [
            i.name,
            { text: String(i.quantity), alignment: 'right' },
            { text: money(i.unitPrice), alignment: 'right' },
            { text: money(i.lineTotal), alignment: 'right' }
          ])
        ]
      },
      layout: 'lightHorizontalLines'
    });
    content.push({ text: 'Grand total: QAR ' + money(po.grandTotal), style: 'total' });

    const doc: any = {
      pageSize: 'A4',
      pageMargins: [40, 95, 40, 75],
      header: { image: headerImg, width: 595, height: 70 },
      footer: (currentPage: number, pageCount: number) => ({
        stack: [
          { text: 'Page ' + currentPage + ' of ' + pageCount, alignment: 'center', fontSize: 8, margin: [0, 0, 0, 3] },
          { image: footerImg, width: 595, height: 40 }
        ]
      }),
      content,
      defaultStyle: { fontSize: 10 },
      styles: {
        title: { fontSize: 20, bold: true, margin: [0, 0, 0, 2] },
        poNumber: { fontSize: 14, color: '#2f5bea', bold: true },
        th: { bold: true, fillColor: '#f0f2f7' },
        total: { fontSize: 12, bold: true, alignment: 'right', margin: [0, 12, 0, 0] }
      }
    };
    // Draft PO: DRAFT watermark instead of the "Issued on" line
    if (!po.issuedAt) {
      doc.watermark = { text: 'DRAFT', color: '#808080', opacity: 0.2, bold: true };
    }

    return { pdf: pdfMake.createPdf(doc), po };
  }

  async downloadPdf() {
    const cur = this.currentPo();
    if (!cur || this.busy()) return;
    this.busy.set(true);
    try {
      const { pdf, po } = await this.buildPdf(cur.id);
      pdf.download(po.poNumber + '.pdf');
      this.error.set('');
    } catch (e: any) {
      this.error.set(e?.message ?? 'Could not build the PDF');
    } finally {
      this.busy.set(false);
    }
  }

  // Shows the PDF inside the app (an overlay with an iframe): no pop-up that a browser could block
  async openPdf(poId: number) {
    if (this.busy()) return;
    this.busy.set(true);
    try {
      const { pdf, po } = await this.buildPdf(poId);
      const blob = await this.getBlob(pdf);
      this.closePdf();
      this.pdfObjectUrl = URL.createObjectURL(blob);
      this.pdfTitle.set(po.poNumber);
      this.pdfUrl.set(this.sanitizer.bypassSecurityTrustResourceUrl(this.pdfObjectUrl));
      this.error.set('');
    } catch (e: any) {
      this.error.set(e?.message ?? 'Could not build the PDF');
    } finally {
      this.busy.set(false);
    }
  }

  viewPdf() {
    const cur = this.currentPo();
    if (cur) this.openPdf(cur.id);
  }

  closePdf() {
    if (this.pdfObjectUrl) {
      URL.revokeObjectURL(this.pdfObjectUrl);
      this.pdfObjectUrl = null;
    }
    this.pdfUrl.set(null);
  }
}