package io.github.zainbits.vitreous

/**
 * Counters for tuning. They are plain fields touched only on the UI thread, so they cost
 * nothing worth measuring; read and reset them from a debug overlay.
 */
public object VitreousDebug {
    /** Times a [glassSource] recorded its content. */
    public var sourceRecords: Long = 0

    /** Times a glass surface re-recorded on the UI thread (scrolling behind it should not). */
    public var glassRecords: Long = 0

    /** Times a glass effect (shader uniforms + blur) was rebuilt. */
    public var effectBuilds: Long = 0

    /**
     * Experiment knob: the blur reads a copy this many times smaller in each direction
     * (1 = full size, 0 = automatic from the blur radius). Read when a glass surface next draws.
     */
    public var blurDownsample: Int = 0

    /**
     * Experiment knob: 1 = 12-tap blur inside the glass shader on a small copy (default; fewest
     * render passes), 0 = the platform blur effect (several passes per glass pass).
     */
    public var blurMethod: Int = 1

    public fun reset() {
        sourceRecords = 0
        glassRecords = 0
        effectBuilds = 0
    }
}
