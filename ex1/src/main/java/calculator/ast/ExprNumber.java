package calculator.ast;

import java.util.Map;

import calculator.ast.finalvalues.FinalValue;
import calculator.ast.finalvalues.Number;

public class ExprNumber extends Expr {
	private int value;

	public ExprNumber(int value) {
		super();
		this.value = value;
	}

	public ExprNumber(String value) {
		this.value = Integer.parseInt(value);
	}

	@Override
	public String toString() {
		return "" + value;
	}

	@Override
	public FinalValue evaluate(Map<String, FinalValue> ctx) {
		return new Number(this.value);
	}

}
