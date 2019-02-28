package calculator.ast;

import java.util.Map;

import calculator.ast.finalvalues.FinalValue;
import calculator.ast.finalvalues.Variable;

public class ExprIdentifier extends Expr implements FinalValue {
	private String value;

	public ExprIdentifier(String value) {
		this.value = value;
	}

	@Override
	public String toString() {
		return value;
	}

	@Override
	public FinalValue evaluate(Map<String, FinalValue> ctx) {
		return ctx.containsKey(this.value) ? ctx.get(this.value) : this;
	}

}
