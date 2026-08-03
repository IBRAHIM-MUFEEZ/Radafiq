export function formatMoney(amount: number): string {
  if (isNaN(amount)) return '₹0.00';
  // en-IN locale produces Indian number grouping: 1,00,000.00
  return new Intl.NumberFormat('en-IN', {
    style: 'currency',
    currency: 'INR',
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
    currencyDisplay: 'symbol',
  }).format(amount).replace(/^₹\s*/, '₹'); // ensure no space after ₹
}

export function formatDate(dateStr: string): string {
  if (!dateStr) return '';
  // Parse ISO YYYY-MM-DD as local midnight to avoid timezone shift from UTC parsing
  const [y, m, d] = dateStr.split('-').map(Number);
  if (!y || !m || !d) return dateStr;
  const date = new Date(y, m - 1, d);
  if (isNaN(date.getTime())) return dateStr;
  return date.toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' });
}

export function todayString(): string {
  return new Date().toISOString().split('T')[0];
}

export function getInitials(name: string): string {
  // BUG-07 fix: handle empty string before split/map
  if (!name || !name.trim()) return 'RA';
  return name
    .trim()
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map(w => w[0]?.toUpperCase() || '')
    .join('') || 'RA';
}

export function getGreeting(): string {
  const hour = new Date().getHours();
  if (hour < 12) return 'Good Morning,';
  if (hour < 17) return 'Good Afternoon,';
  return 'Good Evening,';
}

export function currentTimestampLabel(): string {
  return new Date().toLocaleString('en-IN', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    hour12: true,
  });
}

/**
 * CSP-safe math expression evaluator.
 *
 * Supports: + - * /  parentheses  decimals  whitespace
 * Does NOT use eval() or new Function() — safe under strict script-src CSP.
 *
 * Grammar (recursive descent):
 *   expr   = term   (('+' | '-') term)*
 *   term   = factor (('*' | '/') factor)*
 *   factor = NUMBER | '(' expr ')' | '-' factor
 */
export function evaluateExpression(expr: string): number | null {
  if (!expr || !expr.trim()) return null;

  // Reject anything that isn't digits, operators, parens, dots, or whitespace
  if (!/^[\d\s+\-*/().]+$/.test(expr)) return null;

  const src = expr.replace(/\s+/g, ''); // strip whitespace
  let pos = 0;

  function peek(): string { return src[pos] ?? ''; }
  function consume(): string { return src[pos++] ?? ''; }

  function parseNumber(): number | null {
    let s = '';
    if (peek() === '-') { s += consume(); }
    while (/[\d.]/.test(peek())) { s += consume(); }
    if (!s || s === '-' || s === '.') return null;
    const n = parseFloat(s);
    return isFinite(n) ? n : null;
  }

  function parseFactor(): number | null {
    if (peek() === '(') {
      consume(); // '('
      const val = parseExpr();
      if (peek() !== ')') return null;
      consume(); // ')'
      return val;
    }
    if (peek() === '-') {
      consume();
      const val = parseFactor();
      return val === null ? null : -val;
    }
    return parseNumber();
  }

  function parseTerm(): number | null {
    let left = parseFactor();
    if (left === null) return null;
    while (peek() === '*' || peek() === '/') {
      const op = consume();
      const right = parseFactor();
      if (right === null) return null;
      if (op === '*') left *= right;
      else {
        if (right === 0) return null; // division by zero
        left /= right;
      }
    }
    return left;
  }

  function parseExpr(): number | null {
    let left = parseTerm();
    if (left === null) return null;
    while (peek() === '+' || peek() === '-') {
      const op = consume();
      const right = parseTerm();
      if (right === null) return null;
      if (op === '+') left += right;
      else left -= right;
    }
    return left;
  }

  try {
    const result = parseExpr();
    // Ensure we consumed the entire input (no trailing garbage)
    if (pos !== src.length) return null;
    if (result === null || !isFinite(result)) return null;
    return result;
  } catch {
    return null;
  }
}
