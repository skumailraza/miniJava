package analysis;

import minijava.ast.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Analysis {

    private final MJProgram prog;
    private List<TypeError> typeErrors = new ArrayList<>();

    public Analysis(MJProgram prog) {
        this.prog = prog;
    }

    void addError(MJElement element, String message) {
        typeErrors.add(new TypeError(element, message));
    }

    public List<TypeError> getTypeErrors() {
        return new ArrayList<>(typeErrors);
    }

    public void check() {
        AnalysisVisitor av = new AnalysisVisitor(this.typeErrors);
        prog.accept(av);
    }

}
