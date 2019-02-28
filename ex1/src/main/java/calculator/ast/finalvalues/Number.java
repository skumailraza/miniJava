package calculator.ast.finalvalues;

public class Number implements FinalValue {
    private int value;

    public Number(int v) {
        this.value = v;
    }

    public int getValue() {
        return value;
    }
}
