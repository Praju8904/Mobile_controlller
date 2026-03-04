package com.prajwal.myfirstapp.mathstudio;

public class ExpressionEngine {

    public enum AngleMode { DEG, RAD, GRAD }

    private String expr;
    private int pos;
    private AngleMode angleMode;

    private ExpressionEngine(String expression, AngleMode mode) {
        this.expr = expression.trim();
        this.pos = 0;
        this.angleMode = mode;
    }

    public static double evaluate(String expression, AngleMode mode) throws Exception {
        if (expression == null || expression.trim().isEmpty()) throw new Exception("Empty expression");
        String processed = preprocess(expression);
        ExpressionEngine engine = new ExpressionEngine(processed, mode);
        double result = engine.parseExpression();
        if (engine.pos < engine.expr.length()) throw new Exception("Unexpected character: " + engine.expr.charAt(engine.pos));
        if (Double.isNaN(result)) throw new Exception("Undefined result");
        if (Double.isInfinite(result)) throw new Exception("Division by zero or overflow");
        return result;
    }

    public static double evaluate(String expression) throws Exception {
        return evaluate(expression, AngleMode.DEG);
    }

    private static String preprocess(String s) {
        s = s.replaceAll("\\s+", "");
        s = s.replace("π", "pi").replace("φ", "phi");
        s = s.replace("×", "*").replace("÷", "/").replace("−", "-");
        // implicit multiplication: 2( -> 2*(, )( -> )*(, 2pi -> 2*pi
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            sb.append(c);
            if (i + 1 < s.length()) {
                char next = s.charAt(i + 1);
                if ((Character.isDigit(c) || c == ')') && (next == '(' || Character.isLetter(next))) {
                    sb.append('*');
                }
                if (c == ')' && next == '(') {
                    sb.append('*');
                }
            }
        }
        return sb.toString();
    }

    private double parseExpression() throws Exception {
        return parseAddSub();
    }

    private double parseAddSub() throws Exception {
        double left = parseMulDiv();
        while (pos < expr.length()) {
            char op = expr.charAt(pos);
            if (op == '+') { pos++; left += parseMulDiv(); }
            else if (op == '-') { pos++; left -= parseMulDiv(); }
            else break;
        }
        return left;
    }

    private double parseMulDiv() throws Exception {
        double left = parsePower();
        while (pos < expr.length()) {
            char op = expr.charAt(pos);
            if (op == '*') { pos++; left *= parsePower(); }
            else if (op == '/') {
                pos++;
                double right = parsePower();
                if (right == 0) throw new Exception("Division by zero");
                left /= right;
            }
            else if (op == '%') {
                pos++;
                double right = parsePower();
                if (right == 0) throw new Exception("Modulo by zero");
                left %= right;
            }
            else break;
        }
        return left;
    }

    private double parsePower() throws Exception {
        double base = parseUnary();
        if (pos < expr.length() && expr.charAt(pos) == '^') {
            pos++;
            double exp = parseUnary();
            return Math.pow(base, exp);
        }
        return base;
    }

    private double parseUnary() throws Exception {
        if (pos < expr.length() && expr.charAt(pos) == '-') {
            pos++;
            return -parseUnary();
        }
        if (pos < expr.length() && expr.charAt(pos) == '+') {
            pos++;
            return parseUnary();
        }
        return parseFactorial();
    }

    private double parseFactorial() throws Exception {
        double val = parsePrimary();
        while (pos < expr.length() && expr.charAt(pos) == '!') {
            pos++;
            val = factorial(val);
        }
        return val;
    }

    private double parsePrimary() throws Exception {
        if (pos >= expr.length()) throw new Exception("Unexpected end of expression");

        char c = expr.charAt(pos);

        // Number
        if (Character.isDigit(c) || c == '.') {
            return parseNumber();
        }

        // Parentheses
        if (c == '(') {
            pos++;
            double val = parseExpression();
            if (pos >= expr.length() || expr.charAt(pos) != ')') throw new Exception("Missing closing parenthesis");
            pos++;
            return val;
        }

        // Functions and constants
        if (Character.isLetter(c)) {
            return parseFunctionOrConstant();
        }

        throw new Exception("Unexpected character: " + c);
    }

    private double parseNumber() throws Exception {
        int start = pos;
        while (pos < expr.length() && (Character.isDigit(expr.charAt(pos)) || expr.charAt(pos) == '.')) {
            pos++;
        }
        // Scientific notation
        if (pos < expr.length() && (expr.charAt(pos) == 'e' || expr.charAt(pos) == 'E')) {
            pos++;
            if (pos < expr.length() && (expr.charAt(pos) == '+' || expr.charAt(pos) == '-')) pos++;
            while (pos < expr.length() && Character.isDigit(expr.charAt(pos))) pos++;
        }
        String numStr = expr.substring(start, pos);
        try {
            return Double.parseDouble(numStr);
        } catch (NumberFormatException e) {
            throw new Exception("Invalid number: " + numStr);
        }
    }

    private double parseFunctionOrConstant() throws Exception {
        int start = pos;
        while (pos < expr.length() && (Character.isLetterOrDigit(expr.charAt(pos)) || expr.charAt(pos) == '_')) {
            pos++;
        }
        String name = expr.substring(start, pos);

        // Constants
        switch (name.toLowerCase()) {
            case "pi": return Math.PI;
            case "e": return Math.E;
            case "phi": return (1 + Math.sqrt(5)) / 2.0;
            case "inf": return Double.POSITIVE_INFINITY;
        }

        // Functions
        if (pos < expr.length() && expr.charAt(pos) == '(') {
            pos++;
            double arg = parseExpression();
            if (pos >= expr.length() || expr.charAt(pos) != ')') throw new Exception("Missing ) after function " + name);
            pos++;
            return applyFunction(name.toLowerCase(), arg);
        }

        // nPr and nCr with comma notation: nPr(n,r)
        throw new Exception("Unknown identifier: " + name);
    }

    private double applyFunction(String name, double arg) throws Exception {
        switch (name) {
            case "sin": return Math.sin(toRad(arg));
            case "cos": return Math.cos(toRad(arg));
            case "tan": {
                double rad = toRad(arg);
                double v = Math.tan(rad);
                if (Double.isInfinite(v)) throw new Exception("tan undefined");
                return v;
            }
            case "asin": return fromRad(Math.asin(arg));
            case "arcsin": return fromRad(Math.asin(arg));
            case "acos": return fromRad(Math.acos(arg));
            case "arccos": return fromRad(Math.acos(arg));
            case "atan": return fromRad(Math.atan(arg));
            case "arctan": return fromRad(Math.atan(arg));
            case "sinh": return Math.sinh(arg);
            case "cosh": return Math.cosh(arg);
            case "tanh": return Math.tanh(arg);
            case "asinh": return Math.log(arg + Math.sqrt(arg * arg + 1));
            case "acosh": return Math.log(arg + Math.sqrt(arg * arg - 1));
            case "atanh": return 0.5 * Math.log((1 + arg) / (1 - arg));
            case "log": case "log10": return Math.log10(arg);
            case "ln": return Math.log(arg);
            case "log2": return Math.log(arg) / Math.log(2);
            case "sqrt": return Math.sqrt(arg);
            case "cbrt": return Math.cbrt(arg);
            case "abs": return Math.abs(arg);
            case "floor": return Math.floor(arg);
            case "ceil": return Math.ceil(arg);
            case "round": return (double) Math.round(arg);
            case "exp": return Math.exp(arg);
            case "fact": return factorial(arg);
            case "deg": return Math.toDegrees(arg);
            case "rad": return Math.toRadians(arg);
            case "sign": return Math.signum(arg);
            case "sq": return arg * arg;
            case "cube": return arg * arg * arg;
            default: throw new Exception("Unknown function: " + name);
        }
    }

    private double toRad(double angle) {
        switch (angleMode) {
            case DEG: return Math.toRadians(angle);
            case GRAD: return angle * Math.PI / 200.0;
            default: return angle;
        }
    }

    private double fromRad(double rad) {
        switch (angleMode) {
            case DEG: return Math.toDegrees(rad);
            case GRAD: return rad * 200.0 / Math.PI;
            default: return rad;
        }
    }

    private static double factorial(double n) throws Exception {
        if (n < 0) throw new Exception("Factorial of negative number");
        if (n > 170) throw new Exception("Factorial overflow");
        long ni = Math.round(n);
        if (Math.abs(ni - n) > 1e-9) throw new Exception("Factorial requires a whole number");
        double result = 1;
        for (long i = 2; i <= ni; i++) result *= i;
        return result;
    }

    public static double nPr(double n, double r) throws Exception {
        if (n < 0 || r < 0 || r > n) throw new Exception("Invalid nPr arguments");
        return factorial(n) / factorial(n - r);
    }

    public static double nCr(double n, double r) throws Exception {
        if (n < 0 || r < 0 || r > n) throw new Exception("Invalid nCr arguments");
        return factorial(n) / (factorial(r) * factorial(n - r));
    }

    public static String formatResult(double value) {
        if (Double.isNaN(value)) return "Error";
        if (Double.isInfinite(value)) return value > 0 ? "∞" : "-∞";
        if (value == Math.floor(value) && Math.abs(value) < 1e15) {
            long lv = (long) value;
            return String.valueOf(lv);
        }
        String s = String.format("%.10g", value);
        if (s.contains(".")) {
            s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        }
        return s;
    }
}
