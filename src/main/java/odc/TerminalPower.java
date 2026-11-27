package odc;
/** Window property and authoritative power gate, independent of dashboard refreshes. */
final class TerminalPower {
    static final int PROPERTY=900;
    private TerminalPower(){}
    static boolean operational(boolean online,boolean fed){return online&&fed;}
}
