package translation;

import frontend.AstPrinter;
import minijava.ast.*;
import minillvm.ast.*;

import java.util.HashMap;
import java.util.Map;

public class Translator {

    private MJProgram javaProg;
    Map<MJElement, MJElement> analysisInfo;

    public Translator(MJProgram javaProg, Map<MJElement, MJElement> ai) {
        this.javaProg = javaProg;
        this.analysisInfo = ai;
    }

    public Prog translate() {
        TranslatorVisitor translationVisitor = new TranslatorVisitor();
        translationVisitor.setAnalysisInfo(this.analysisInfo);
        javaProg.accept(translationVisitor);
        return translationVisitor.getProg();
    }

}
