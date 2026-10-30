package dev.groundburst.content.defence;

/** A block entity that fires interceptors (the Interceptor Battery, the Interceptor Complex): told how each of its own came out. */
public interface InterceptorLauncher {
    /**
     * How an interceptor came out: its missile shot down; missed (the kill roll failed, or the missile was not where it
     * was reckoned); spent (its missile gone before it got there: another interceptor's kill, or down already); fried by
     * an EMP on the way (DESIGN.md §21.4).
     */
    enum Outcome { KILLED, MISSED, SPENT, FRIED }

    void reported(Outcome outcome);
}
