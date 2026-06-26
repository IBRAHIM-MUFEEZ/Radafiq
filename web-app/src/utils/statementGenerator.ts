import { jsPDF } from 'jspdf';
import type { CustomerSummary, CustomerTransaction, SettlementHistoryEntry, SavingsEntry } from '../types/models';

// ── Font helper ───────────────────────────────────────────────────────────────
const FONT_FAMILY = 'helvetica' as const;

function setFont(doc: jsPDF, style: 'normal' | 'bold' | 'italic' | 'bolditalic') {
  doc.setFont(FONT_FAMILY, style);
}

// ── Money formatter ───────────────────────────────────────────────────────────
// Uses "Rs." prefix — ₹ (U+20B9) is not in any jsPDF built-in font.
// Strips non-breaking spaces (U+00A0) that en-IN locale inserts as thousands
// separators on some platforms, replacing them with regular commas.
function pdfMoney(amount: number): string {
  if (isNaN(amount)) return 'Rs. 0.00';
  const formatted = new Intl.NumberFormat('en-IN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })
    .format(amount)
    .replace(/\u00A0/g, ',')
    .replace(/\u202F/g, ',');   // narrow no-break space (some browsers)
  return `Rs. ${formatted}`;
}

// ── Palette ───────────────────────────────────────────────────────────────────

const LIGHT_BG_PAGE    = [240, 248, 255] as const;
const LIGHT_BG_RAISED  = [255, 255, 255] as const;
const LIGHT_BG_SOFT    = [216, 239, 250] as const;
const LIGHT_TEXT_PRI   = [7,   21,  37 ] as const;
const LIGHT_TEXT_MUTED = [58,  127, 168] as const;
const LIGHT_OUTLINE    = [168, 212, 234] as const;

const DARK_BG_DEEP     = [7,   21,  37 ] as const;
const DARK_BG_SOFT     = [12,  32,  53 ] as const;
const DARK_BG_RAISED   = [16,  40,  64 ] as const;
const DARK_TEXT_PRI    = [232, 244, 255] as const;
const DARK_TEXT_MUTED  = [107, 174, 212] as const;
const DARK_OUTLINE     = [26,  64,  96 ] as const;

const PRIMARY        = [26,  143, 212] as const;
const TEAL           = [26,  171, 207] as const;
const GREEN_BRAND    = [29,  217, 160] as const;
const RED_ACCENT     = [232, 68,  90 ] as const;
const ORANGE_PENDING = [245, 158, 11 ] as const;

type RGB = readonly [number, number, number];

interface Theme {
  BG_DEEP:      RGB;
  BG_SOFT:      RGB;
  BG_RAISED:    RGB;
  TEXT_PRIMARY: RGB;
  TEXT_MUTED:   RGB;
  OUTLINE:      RGB;
}

function getTheme(isDark: boolean): Theme {
  return isDark
    ? { BG_DEEP: DARK_BG_DEEP, BG_SOFT: DARK_BG_SOFT, BG_RAISED: DARK_BG_RAISED,
        TEXT_PRIMARY: DARK_TEXT_PRI, TEXT_MUTED: DARK_TEXT_MUTED, OUTLINE: DARK_OUTLINE }
    : { BG_DEEP: LIGHT_BG_PAGE, BG_SOFT: LIGHT_BG_SOFT, BG_RAISED: LIGHT_BG_RAISED,
        TEXT_PRIMARY: LIGHT_TEXT_PRI, TEXT_MUTED: LIGHT_TEXT_MUTED, OUTLINE: LIGHT_OUTLINE };
}

// ── Draw helpers ──────────────────────────────────────────────────────────────

function setFill(doc: jsPDF, rgb: RGB) { doc.setFillColor(rgb[0], rgb[1], rgb[2]); }
function setDraw(doc: jsPDF, rgb: RGB) { doc.setDrawColor(rgb[0], rgb[1], rgb[2]); }
function setTextColor(doc: jsPDF, rgb: RGB) { doc.setTextColor(rgb[0], rgb[1], rgb[2]); }

function roundedRect(
  doc: jsPDF, x: number, y: number, w: number, h: number,
  r: number, fill: RGB, stroke?: RGB
) {
  setFill(doc, fill);
  if (stroke) {
    setDraw(doc, stroke);
    doc.roundedRect(x, y, w, h, r, r, 'FD');
  } else {
    doc.roundedRect(x, y, w, h, r, r, 'F');
  }
}

// ── Page background ───────────────────────────────────────────────────────────

function drawBackground(doc: jsPDF, t: Theme, isDark: boolean, pw: number, ph: number) {
  setFill(doc, t.BG_DEEP);
  doc.rect(0, 0, pw, ph, 'F');

  if (isDark) {
    doc.setFillColor(12, 32, 53);
    doc.setGState(doc.GState({ opacity: 0.55 }));
    doc.rect(0, ph * 0.4, pw, ph * 0.6, 'F');
    doc.setGState(doc.GState({ opacity: 1 }));

    doc.setFillColor(26, 143, 212);
    doc.setGState(doc.GState({ opacity: 0.18 }));
    doc.circle(pw * 0.18, ph * 0.10, 28, 'F');
    doc.setFillColor(29, 217, 160);
    doc.setGState(doc.GState({ opacity: 0.12 }));
    doc.circle(pw * 0.92, ph * 0.18, 24, 'F');
    doc.setFillColor(26, 171, 207);
    doc.setGState(doc.GState({ opacity: 0.10 }));
    doc.circle(pw * 0.76, ph * 0.86, 22, 'F');
    doc.setGState(doc.GState({ opacity: 1 }));
  } else {
    doc.setFillColor(234, 246, 255);
    doc.setGState(doc.GState({ opacity: 0.6 }));
    doc.rect(0, ph * 0.45, pw, ph * 0.55, 'F');
    doc.setGState(doc.GState({ opacity: 1 }));

    doc.setFillColor(26, 143, 212);
    doc.setGState(doc.GState({ opacity: 0.10 }));
    doc.circle(pw * 0.18, ph * 0.10, 25, 'F');
    doc.setFillColor(29, 217, 160);
    doc.setGState(doc.GState({ opacity: 0.08 }));
    doc.circle(pw * 0.88, ph * 0.15, 20, 'F');
    doc.setGState(doc.GState({ opacity: 1 }));
  }
}

// ── Header ────────────────────────────────────────────────────────────────────

function drawHeader(
  doc: jsPDF, t: Theme, customer: CustomerSummary, pw: number, startY: number
): number {
  const logoSize = 14;
  const logoX = 14;
  const logoY = startY;

  // Logo circle
  setFill(doc, PRIMARY);
  doc.circle(logoX + logoSize / 2, logoY + logoSize / 2, logoSize / 2, 'F');
  setFont(doc, 'bold');
  doc.setFontSize(9);
  setTextColor(doc, [255, 255, 255]);
  doc.text('R', logoX + logoSize / 2, logoY + logoSize / 2 + 3, { align: 'center' });

  // App name
  setFont(doc, 'bold');
  doc.setFontSize(13);
  setTextColor(doc, PRIMARY);
  doc.text('Radafiq', logoX + logoSize + 4, logoY + 9);

  setFont(doc, 'normal');
  doc.setFontSize(7);
  setTextColor(doc, t.TEXT_MUTED);
  doc.text('Customer Statement', logoX + logoSize + 4, logoY + 14);

  // Divider
  const lineY = logoY + logoSize + 5;
  setDraw(doc, t.OUTLINE);
  doc.setLineWidth(0.3);
  doc.line(14, lineY, pw - 14, lineY);

  // Customer name
  setFont(doc, 'bold');
  doc.setFontSize(16);
  setTextColor(doc, t.TEXT_PRIMARY);
  doc.text(customer.name, 14, lineY + 9);

  // Generated date
  const now = new Date();
  const dateStr = now.toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' });
  setFont(doc, 'normal');
  doc.setFontSize(7);
  setTextColor(doc, t.TEXT_MUTED);
  doc.text(`Statement generated on ${dateStr}`, 14, lineY + 15);

  return lineY + 22;
}

// ── Metric box row ────────────────────────────────────────────────────────────

function drawMetricBoxRow(
  doc: jsPDF,
  t: Theme,
  boxes: Array<{ label: string; value: string; color: RGB }>,
  pw: number,
  startY: number
): number {
  const gap  = 3;
  const boxW = (pw - 28 - gap * (boxes.length - 1)) / boxes.length;
  const boxH = 18;

  boxes.forEach((box, i) => {
    const x = 14 + i * (boxW + gap);
    roundedRect(doc, x, startY, boxW, boxH, 3, t.BG_RAISED, t.OUTLINE);
    doc.setLineWidth(0.2);

    setFont(doc, 'bold');
    doc.setFontSize(6);
    setTextColor(doc, t.TEXT_MUTED);
    doc.text(box.label, x + 4, startY + 6);

    setFont(doc, 'bold');
    doc.setFontSize(9);
    setTextColor(doc, box.color);
    doc.text(box.value, x + 4, startY + 14);
  });

  return startY + boxH + 3;
}

// ── Dues overview ─────────────────────────────────────────────────────────────

function drawDuesOverview(
  doc: jsPDF,
  t: Theme,
  customer: CustomerSummary,
  pw: number,
  startY: number
): number {
  const visible = customer.transactions.filter(t2 => {
    if (!t2.emiGroupId) return true;
    const d = new Date(t2.transactionDate);
    const now = new Date();
    return d.getFullYear() < now.getFullYear() ||
      (d.getFullYear() === now.getFullYear() && d.getMonth() <= now.getMonth());
  });

  const splitMap = new Map<string, CustomerTransaction[]>();
  const logicalGroups: CustomerTransaction[][] = [];
  visible.forEach(tx => {
    if (tx.splitGroupId) {
      if (!splitMap.has(tx.splitGroupId)) {
        splitMap.set(tx.splitGroupId, []);
        logicalGroups.push(splitMap.get(tx.splitGroupId)!);
      }
      splitMap.get(tx.splitGroupId)!.push(tx);
    } else {
      logicalGroups.push([tx]);
    }
  });

  const total   = logicalGroups.length;
  const settled = logicalGroups.filter(g => g.every(t2 => t2.isSettled)).length;
  const partial = logicalGroups.filter(g => !g.every(t2 => t2.isSettled) && g.some(t2 => t2.partialPaidAmount > 0)).length;
  const unpaid  = logicalGroups.filter(g => !g.every(t2 => t2.isSettled) && !g.some(t2 => t2.partialPaidAmount > 0)).length;

  const amtSettled = logicalGroups
    .filter(g => g.every(t2 => t2.isSettled))
    .reduce((s, g) => s + g.reduce((a, t2) => a + t2.amount, 0), 0);
  const amtPartial = logicalGroups
    .filter(g => !g.every(t2 => t2.isSettled) && g.some(t2 => t2.partialPaidAmount > 0))
    .reduce((s, g) => s + g.reduce((a, t2) => a + Math.max(0, t2.amount - t2.partialPaidAmount), 0), 0);
  const amtUnpaid  = logicalGroups
    .filter(g => !g.every(t2 => t2.isSettled) && !g.some(t2 => t2.partialPaidAmount > 0))
    .reduce((s, g) => s + g.reduce((a, t2) => a + t2.amount, 0), 0);

  // Section label
  setFont(doc, 'bold');
  doc.setFontSize(7);
  setTextColor(doc, t.TEXT_MUTED);
  doc.text('DUES OVERVIEW', 14, startY);
  startY += 4;

  const gap   = 3;
  const cardW = (pw - 28 - gap * 2) / 3;
  const cardH = 22;

  const dueBoxes = [
    { label: 'Settled',      count: `${settled} of ${total}`, amount: pdfMoney(amtSettled), color: GREEN_BRAND },
    { label: 'Partial Paid', count: `${partial} of ${total}`, amount: pdfMoney(amtPartial), color: ORANGE_PENDING },
    { label: 'Unpaid',       count: `${unpaid} of ${total}`,  amount: pdfMoney(amtUnpaid),  color: RED_ACCENT },
  ];

  dueBoxes.forEach((box, i) => {
    const x = 14 + i * (cardW + gap);
    roundedRect(doc, x, startY, cardW, cardH, 3, t.BG_RAISED, t.OUTLINE);
    doc.setLineWidth(0.2);

    // Left accent bar
    setFill(doc, box.color);
    doc.rect(x, startY, 2, cardH, 'F');

    setFont(doc, 'bold');
    doc.setFontSize(6);
    setTextColor(doc, t.TEXT_MUTED);
    doc.text(box.label, x + 5, startY + 6);

    setFont(doc, 'bold');
    doc.setFontSize(10);
    setTextColor(doc, box.color);
    doc.text(box.count, x + 5, startY + 14);

    setFont(doc, 'normal');
    doc.setFontSize(6);
    setTextColor(doc, t.TEXT_MUTED);
    doc.text(box.amount, x + 5, startY + 20);
  });

  return startY + cardH + 3;
}

// ── Section header ────────────────────────────────────────────────────────────

function drawSectionHeader(
  doc: jsPDF, t: Theme, title: string, pw: number, startY: number
): number {
  setFont(doc, 'bold');
  doc.setFontSize(7);
  setTextColor(doc, t.TEXT_MUTED);
  doc.text(title.toUpperCase(), 14, startY);

  setDraw(doc, t.OUTLINE);
  doc.setLineWidth(0.3);
  doc.line(14, startY + 2, pw - 14, startY + 2);

  return startY + 7;
}

// ── Transaction row ───────────────────────────────────────────────────────────

function drawTransactionRow(
  doc: jsPDF,
  t: Theme,
  txn: CustomerTransaction,
  pw: number,
  startY: number
): number {
  const rowH  = 14;
  const left  = 14;
  const right = pw - 14;

  const statusColor: RGB = txn.isSettled
    ? GREEN_BRAND
    : txn.partialPaidAmount > 0
    ? ORANGE_PENDING
    : RED_ACCENT;

  roundedRect(doc, left, startY, right - left, rowH, 2.5, t.BG_RAISED);

  // Left status bar
  setFill(doc, statusColor);
  doc.rect(left, startY, 1.5, rowH, 'F');

  // Date
  setFont(doc, 'bold');
  doc.setFontSize(6);
  setTextColor(doc, t.TEXT_MUTED);
  doc.text(txn.transactionDate, left + 4, startY + 5.5);

  // Name
  setFont(doc, 'bold');
  doc.setFontSize(7.5);
  setTextColor(doc, t.TEXT_PRIMARY);
  const maxNameW = right - left - 80;
  const name = doc.splitTextToSize(txn.name, maxNameW)[0] as string;
  doc.text(name, left + 30, startY + 5.5);

  // Account
  setFont(doc, 'normal');
  doc.setFontSize(6);
  setTextColor(doc, t.TEXT_MUTED);
  doc.text(txn.accountName, left + 30, startY + 10.5);

  // Amount
  setFont(doc, 'bold');
  doc.setFontSize(8);
  setTextColor(doc, PRIMARY);
  doc.text(pdfMoney(txn.amount), right - 4, startY + 5.5, { align: 'right' });

  // Status — plain ASCII, no special characters
  const statusLabel = txn.isSettled
    ? 'Settled'
    : txn.partialPaidAmount > 0
    ? `Partial ${pdfMoney(txn.partialPaidAmount)}`
    : 'Unpaid';
  setFont(doc, 'normal');
  doc.setFontSize(6);
  setTextColor(doc, statusColor);
  doc.text(statusLabel, right - 4, startY + 10.5, { align: 'right' });

  // Row separator
  setDraw(doc, t.OUTLINE);
  doc.setLineWidth(0.2);
  doc.line(left, startY + rowH, right, startY + rowH);

  return startY + rowH + 1.5;
}

// ── Split transaction row ─────────────────────────────────────────────────────

function drawSplitRow(
  doc: jsPDF,
  t: Theme,
  splits: CustomerTransaction[],
  pw: number,
  startY: number
): number {
  const first      = splits[0];
  const total      = splits.reduce((s, tx) => s + tx.amount, 0);
  const allSettled = splits.every(tx => tx.isSettled);
  const anyPartial = splits.some(tx => tx.partialPaidAmount > 0);
  const statusColor: RGB = allSettled ? GREEN_BRAND : anyPartial ? ORANGE_PENDING : RED_ACCENT;
  const rowH  = 14;
  const left  = 14;
  const right = pw - 14;

  roundedRect(doc, left, startY, right - left, rowH, 2.5, t.BG_RAISED);

  // Left bar — primary blue for split
  setFill(doc, PRIMARY);
  doc.rect(left, startY, 1.5, rowH, 'F');

  // Date
  setFont(doc, 'bold');
  doc.setFontSize(6);
  setTextColor(doc, t.TEXT_MUTED);
  doc.text(first.transactionDate, left + 4, startY + 5.5);

  // Name
  setFont(doc, 'bold');
  doc.setFontSize(7.5);
  setTextColor(doc, t.TEXT_PRIMARY);
  doc.text(first.name, left + 30, startY + 5.5);

  // Split badge — "Split x3" (no special chars)
  setFont(doc, 'bold');
  doc.setFontSize(5.5);
  setTextColor(doc, PRIMARY);
  doc.text(`Split x${splits.length}`, left + 30, startY + 10.5);

  // Amount
  setFont(doc, 'bold');
  doc.setFontSize(8);
  setTextColor(doc, PRIMARY);
  doc.text(pdfMoney(total), right - 4, startY + 5.5, { align: 'right' });

  // Status — plain ASCII only
  const statusLabel = allSettled ? 'Settled' : anyPartial ? 'Partial' : 'Unpaid';
  setFont(doc, 'normal');
  doc.setFontSize(6);
  setTextColor(doc, statusColor);
  doc.text(statusLabel, right - 4, startY + 10.5, { align: 'right' });

  setDraw(doc, t.OUTLINE);
  doc.setLineWidth(0.2);
  doc.line(left, startY + rowH, right, startY + rowH);

  return startY + rowH + 1.5;
}

// ── EMI schedule ──────────────────────────────────────────────────────────────

function drawEmiSchedule(
  doc: jsPDF,
  t: Theme,
  emiTxns: CustomerTransaction[],
  pw: number,
  startY: number,
  checkPb: (y: number, needed: number) => number
): number {
  let y = drawSectionHeader(doc, t, 'EMI Schedule', pw, startY);
  y += 2;

  const grouped = new Map<string, CustomerTransaction[]>();
  emiTxns.forEach(tx => {
    if (!grouped.has(tx.emiGroupId)) grouped.set(tx.emiGroupId, []);
    grouped.get(tx.emiGroupId)!.push(tx);
  });

  grouped.forEach((txns) => {
    const sorted = [...txns].sort((a, b) => a.emiIndex - b.emiIndex);
    const first  = sorted[0];

    // Check if this entire EMI group (header + all rows + spacing) fits
    const groupHeaderH = 5;
    const rowH = 5;
    const groupSpacing = 3;
    const groupHeight = groupHeaderH + sorted.length * rowH + groupSpacing;
    y = checkPb(y, groupHeight);

    setFont(doc, 'bold');
    doc.setFontSize(7.5);
    setTextColor(doc, TEAL);
    // Strip any em-dash variants from the name to avoid encoding issues
    const groupName = first.name.replace(/ [-\u2013\u2014] EMI.*/i, '');
    doc.text(`${groupName} - ${sorted.length} instalments`, 14, y);
    y += 5;

    sorted.forEach(tx => {
      // Check before each individual row to prevent mid-group overflow
      y = checkPb(y, rowH);

      const dotColor: RGB = tx.isSettled ? GREEN_BRAND : ORANGE_PENDING;
      setFill(doc, dotColor);
      doc.circle(18, y - 1.5, 1.2, 'F');

      setFont(doc, 'normal');
      doc.setFontSize(7);
      setTextColor(doc, t.TEXT_MUTED);
      doc.text(`EMI ${tx.emiIndex + 1}/${tx.emiTotal}`, 22, y);
      doc.text(tx.transactionDate, 55, y);
      doc.text(pdfMoney(tx.amount), pw - 14, y, { align: 'right' });
      y += 5;
    });
    y += 3;
  });

  return y;
}

// ── Footer ────────────────────────────────────────────────────────────────────

function drawFooter(
  doc: jsPDF, t: Theme, pageNum: number, ph: number, pw: number, generatedBy: string
) {
  setDraw(doc, t.OUTLINE);
  doc.setLineWidth(0.3);
  doc.line(14, ph - 16, pw - 14, ph - 16);

  setFont(doc, 'normal');
  doc.setFontSize(6.5);
  setTextColor(doc, PRIMARY);
  doc.text(`Generated by: ${generatedBy}`, 14, ph - 10);

  const ts = new Date().toLocaleString('en-IN', {
    day: '2-digit', month: 'short', year: 'numeric',
    hour: '2-digit', minute: '2-digit', hour12: true,
  });
  setTextColor(doc, t.TEXT_MUTED);
  doc.text(`Generated: ${ts}`, 14, ph - 5);
  doc.text(`Page ${pageNum}  Radafiq`, pw - 14, ph - 5, { align: 'right' });
}

// ── Main export ───────────────────────────────────────────────────────────────

export function generateAndDownloadStatement(
  customer: CustomerSummary,
  generatedByName: string = 'Radafiq User',
  isDark: boolean = false,
  settlementHistory?: (SettlementHistoryEntry & { transactionName: string })[],
  savingsEntries?: SavingsEntry[]
): void {
  const t   = getTheme(isDark);
  const doc = new jsPDF({ unit: 'mm', format: 'a4', orientation: 'portrait' });
  const pw  = doc.internal.pageSize.getWidth();
  const ph  = doc.internal.pageSize.getHeight();

  let pageNum = 1;

  function newPage() {
    doc.addPage();
    pageNum++;
    drawBackground(doc, t, isDark, pw, ph);
    return 14;
  }

  function checkPageBreak(y: number, needed: number): number {
    if (y + needed > ph - 20) {
      drawFooter(doc, t, pageNum, ph, pw, generatedByName);
      return newPage();
    }
    return y;
  }

  // Page 1
  drawBackground(doc, t, isDark, pw, ph);

  let y = drawHeader(doc, t, customer, pw, 10);
  y += 5;

  // Summary metrics
  y = drawMetricBoxRow(doc, t, [
    { label: 'Total Used',    value: pdfMoney(customer.totalAmount),     color: PRIMARY },
    { label: 'Customer Paid', value: pdfMoney(customer.creditDueAmount), color: GREEN_BRAND },
    { label: 'Balance Due',   value: pdfMoney(customer.balance),         color: customer.balance > 0 ? RED_ACCENT : GREEN_BRAND },
  ], pw, y);
  y += 4;

  // Dues overview
  y = drawDuesOverview(doc, t, customer, pw, y);
  y += 5;

  // Build ordered groups for rendering
  const visible = customer.transactions
    .filter(tx => {
      if (!tx.emiGroupId) return true;
      const d   = new Date(tx.transactionDate);
      const now = new Date();
      return d.getFullYear() < now.getFullYear() ||
        (d.getFullYear() === now.getFullYear() && d.getMonth() <= now.getMonth());
    })
    .sort((a, b) => b.transactionDate.localeCompare(a.transactionDate) || b.amount - a.amount);

  const splitMap     = new Map<string, CustomerTransaction[]>();
  const orderedGroups: CustomerTransaction[][] = [];
  visible.forEach(tx => {
    if (tx.splitGroupId) {
      if (!splitMap.has(tx.splitGroupId)) {
        splitMap.set(tx.splitGroupId, []);
        orderedGroups.push(splitMap.get(tx.splitGroupId)!);
      }
      splitMap.get(tx.splitGroupId)!.push(tx);
    } else {
      orderedGroups.push([tx]);
    }
  });

  const settledGroups   = orderedGroups.filter(g => g.every(t2 => t2.isSettled));
  const unsettledGroups = orderedGroups.filter(g => !g.every(t2 => t2.isSettled));

  function drawGroupList(groups: CustomerTransaction[][], startY: number): number {
    let yy = startY;
    for (const group of groups) {
      yy = checkPageBreak(yy, 18);
      yy = group.length > 1
        ? drawSplitRow(doc, t, group, pw, yy)
        : drawTransactionRow(doc, t, group[0], pw, yy);
    }
    return yy;
  }

  // Settled transactions
  if (settledGroups.length > 0) {
    y = checkPageBreak(y, 20);
    y = drawSectionHeader(doc, t, `Settled Transactions (${settledGroups.length})`, pw, y);
    y += 2;
    y = drawGroupList(settledGroups, y);
    y += 4;
  }

  // Unsettled transactions
  if (unsettledGroups.length > 0) {
    y = checkPageBreak(y, 20);
    y = drawSectionHeader(doc, t, `Unsettled Transactions (${unsettledGroups.length})`, pw, y);
    y += 2;
    y = drawGroupList(unsettledGroups, y);
    y += 4;
  }

  // EMI schedule
  const emiTxns = customer.transactions.filter(tx => tx.emiGroupId);
  if (emiTxns.length > 0) {
    y += 5;
    y = drawEmiSchedule(doc, t, emiTxns, pw, y, checkPageBreak);
  }

  // Settlement history
  if (settlementHistory && settlementHistory.length > 0) {
    y += 5;
    y = checkPageBreak(y, 20);
    y = drawSectionHeader(doc, t, 'Settlement History', pw, y);
    y += 2;

    const sortedHistory = [...settlementHistory].sort((a, b) => b.timestamp - a.timestamp);
    for (const entry of sortedHistory) {
      y = checkPageBreak(y, 14);
      const rowH  = 14;
      const left  = 14;
      const right = pw - 14;

      const entryColor: RGB = entry.type === 'settled'
        ? GREEN_BRAND
        : entry.type === 'partial'
        ? ORANGE_PENDING
        : RED_ACCENT;

      roundedRect(doc, left, y, right - left, rowH, 2.5, t.BG_RAISED);
      setFill(doc, entryColor);
      doc.rect(left, y, 1.5, rowH, 'F');

      setFont(doc, 'normal');
      doc.setFontSize(6);
      setTextColor(doc, t.TEXT_MUTED);
      doc.text(entry.date, left + 4, y + 5.5);

      setFont(doc, 'bold');
      doc.setFontSize(7.5);
      setTextColor(doc, t.TEXT_PRIMARY);
      const maxNameW = right - left - 90;
      const histName = doc.splitTextToSize(entry.transactionName, maxNameW)[0] as string;
      doc.text(histName, left + 30, y + 5.5);

      setFont(doc, 'normal');
      doc.setFontSize(6);
      setTextColor(doc, t.TEXT_MUTED);
      const typeLabel = entry.type === 'settled' ? 'Settled'
        : entry.type === 'partial' ? 'Partial'
        : 'Unpaid';
      doc.text(typeLabel, left + 30, y + 10.5);

      setFont(doc, 'bold');
      doc.setFontSize(8);
      setTextColor(doc, entryColor);
      doc.text(pdfMoney(entry.amount), right - 4, y + 5.5, { align: 'right' });

      setDraw(doc, t.OUTLINE);
      doc.setLineWidth(0.2);
      doc.line(left, y + rowH, right, y + rowH);

      y += rowH + 1.5;
    }
    y += 4;
  }

  // Savings details
  if (savingsEntries && savingsEntries.length > 0) {
    const sorted = [...savingsEntries].sort((a, b) => b.date.localeCompare(a.date));
    const totalDeposits = sorted.filter(s => s.type === 'deposit').reduce((s, e) => s + e.amount, 0);
    const totalWithdrawals = sorted.filter(s => s.type === 'withdrawal').reduce((s, e) => s + e.amount, 0);
    const netSavings = totalDeposits - totalWithdrawals;

    y += 5;
    y = checkPageBreak(y, 20);
    y = drawSectionHeader(doc, t, 'Savings Details', pw, y);
    y += 2;

    y = drawMetricBoxRow(doc, t, [
      { label: 'Total Deposits',  value: pdfMoney(totalDeposits),   color: GREEN_BRAND },
      { label: 'Total Withdrawn', value: pdfMoney(totalWithdrawals), color: ORANGE_PENDING },
      { label: 'Net Savings',     value: pdfMoney(netSavings),      color: netSavings >= 0 ? GREEN_BRAND : RED_ACCENT },
    ], pw, y);
    y += 4;

    for (const entry of sorted) {
      y = checkPageBreak(y, 14);
      const rowH  = 14;
      const left  = 14;
      const right = pw - 14;

      const isDeposit = entry.type === 'deposit';
      const entryColor: RGB = isDeposit ? GREEN_BRAND : RED_ACCENT;

      roundedRect(doc, left, y, right - left, rowH, 2.5, t.BG_RAISED);
      setFill(doc, entryColor);
      doc.rect(left, y, 1.5, rowH, 'F');

      setFont(doc, 'normal');
      doc.setFontSize(6);
      setTextColor(doc, t.TEXT_MUTED);
      doc.text(entry.date, left + 4, y + 5.5);

      setFont(doc, 'bold');
      doc.setFontSize(7.5);
      setTextColor(doc, t.TEXT_PRIMARY);
      doc.text(isDeposit ? 'Deposit' : 'Withdrawal', left + 30, y + 5.5);

      setFont(doc, 'normal');
      doc.setFontSize(6);
      setTextColor(doc, t.TEXT_MUTED);
      const noteMaxW = right - left - 90;
      const note = entry.note ? doc.splitTextToSize(entry.note, noteMaxW)[0] as string : '';
      if (note) doc.text(note, left + 30, y + 10.5);

      setFont(doc, 'bold');
      doc.setFontSize(8);
      setTextColor(doc, entryColor);
      doc.text(pdfMoney(entry.amount), right - 4, y + 5.5, { align: 'right' });

      setDraw(doc, t.OUTLINE);
      doc.setLineWidth(0.2);
      doc.line(left, y + rowH, right, y + rowH);

      y += rowH + 1.5;
    }
  }

  drawFooter(doc, t, pageNum, ph, pw, generatedByName);

  const safeName = customer.name.replace(/\s+/g, '_');
  doc.save(`statement_${safeName}_${Date.now()}.pdf`);
}
