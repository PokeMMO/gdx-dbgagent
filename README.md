# gdx-dbgagent

Java Agent for debugging common libgdx related issues.

# Debug utilities

* UNDISPOSED (-Dgdxdbg.debug.undisposed=true)
  - Enables debugging if a Disposable object is finalized without being properly disposed.
* DEBUG_UNCLOSED (-Dgdxdbg.debug.unclosed=true)
  - Enables debugging if a java.io.Closeable object is finalized without being properly disposed.
  - Cannot currently debug classes which are loaded before java agent is loaded. :(
* MODIFIABLE_CONSTANTS (-Dgdxdbg.debug.modifiable_constants=true)
  - Enables debugging if certain `modifiable constant`'s values change during runtime.
  - Things constants like Color.WHITE can be accidently modified, leading to unexpected results.
  - This utility will alert you that this has occurred, but not what has caused it.
* GL_THREAD (-Dgdxdbg.debug.gl_thread=true)
  - Enables debugging of methods/constructors called from incorrect threads.
  - Any method or constructor that has an annotation with the name `@RequireGLThread` will throw a warning when called from a thread other than the main GL thread.
  - Certain scene2d methods are included by default in this detection.

# Usage

1. Build the `gdx-dbgagent.jar` file from this project via the instructions in the 'Build' section below
2. Copy the `gdx-dbgagent.jar` to any directory (henceforth called `<cwd>`)
3. Start your libgdx application with `-javaagent:<cwd>/gdx-dbgagent.jar`
    1. when using the command line, it should look like: `java -javaagent:<cwd>/gdx-dbgagent.jar -cp all/the/jars your.main.Class`
    2. when using Eclipse, right-click your class with the `main()` method, goto 'Run As > Run Configurations...' and on the 'Arguments' tab inside the 'VM Arguments:' field enter `-javaagent:<cwd>/gdx-dbgagent.jar`

# Build

1. `./gradlew assemble`
2. see `build/libs/gdx-dbgagent.jar`
