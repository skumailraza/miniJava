package calculator.ast;

public enum Operator {
    Plus("+"), Minus("-"), Times("*"), Div("/"), Equals("=="), Less("<");

    private String stringRepresentation;

    Operator(String s) {
        this.stringRepresentation = s;
    }

    public String toString() {
        return stringRepresentation;
    }
}
