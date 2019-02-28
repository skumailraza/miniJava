package calculator.ast.finalvalues;

public class Variable implements FinalValue {
    private String value;

    public Variable(String v) {
        this.value = v;
    }

    public String getValue() {
        return value;
    }
}
