package analysis;

import frontend.SourcePosition;
import minijava.ast.*;

public class NullType implements MJType {

    @Override
    public int size() {
        return 0;
    }

    @Override
    public void clearAttributes() {

    }

    @Override
    public void clearAttributesLocal() {

    }

    @Override
    public MJElement get(int i) {
        return null;
    }

    @Override
    public MJElement set(int i, MJElement newElement) {
        return null;
    }

    @Override
    public void setParent(MJElement parent) {

    }

    @Override
    public void replaceBy(MJElement other) {

    }

    @Override
    public boolean structuralEquals(MJElement elem) {
        return false;
    }

    @Override
    public <T> T match(minijava.ast.MJElement.Matcher<T> s) {
        return null;
    }

    @Override
    public void match(minijava.ast.MJElement.MatcherVoid s) {

    }

    @Override
    public void accept(Visitor v) {

    }

    @Override
    public MJElement getParent() {
        return null;
    }

    @Override
    public <T> T match(Matcher<T> s) {
        return null;
    }

    @Override
    public void match(MatcherVoid s) {

    }

    @Override
    public MJType copy() {
        return null;
    }

    @Override
    public SourcePosition getSourcePosition() {
        return null;
    }

    @Override
    public void setSourcePosition(SourcePosition sourcePosition) {

    }
}