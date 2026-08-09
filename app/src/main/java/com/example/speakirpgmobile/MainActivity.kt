package com.example.speakirpgmobile

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.GridLayout
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var joystick: JoystickView
    private lateinit var utilityPanel: GridLayout

    private val heldKeys = mutableSetOf<WebKey>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideSystemUi()

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
        }

        webView = createWebView()

        root.addView(
            webView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        // Small joystick, raised above the in-game chat area.
        joystick = JoystickView(this).apply {
            alpha = 0.70f
            onDirectionChanged = { updateMovementKeys(it) }
        }

        root.addView(
            joystick,
            FrameLayout.LayoutParams(dp(138), dp(138)).apply {
                gravity = Gravity.BOTTOM or Gravity.START
                leftMargin = dp(18)
                bottomMargin = dp(112)
            }
        )

        // Permanent SPACE button for basic/auto attack.
        val attack = makeHoldButton(
            "⚔\nSPACE",
            WebKey(" ", "Space")
        ).apply {
            textSize = 13f
            alpha = 0.78f
        }

        root.addView(
            attack,
            FrameLayout.LayoutParams(dp(88), dp(88)).apply {
                gravity = Gravity.CENTER_VERTICAL or Gravity.END
                rightMargin = dp(20)
                topMargin = dp(95)
            }
        )

        // Hamburger menu.
        val menuButton = Button(this).apply {
            text = "☰"
            textSize = 21f
            setTextColor(Color.WHITE)
            setBackgroundColor(0x99000000.toInt())
            alpha = 0.82f

            setOnClickListener {
                utilityPanel.visibility =
                    if (utilityPanel.visibility == View.VISIBLE) {
                        View.GONE
                    } else {
                        View.VISIBLE
                    }
            }
        }

        root.addView(
            menuButton,
            FrameLayout.LayoutParams(dp(56), dp(52)).apply {
                gravity = Gravity.TOP or Gravity.END
                topMargin = dp(12)
                rightMargin = dp(14)
            }
        )

        utilityPanel = buildUtilityPanel()
        utilityPanel.visibility = View.GONE

        root.addView(
            utilityPanel,
            FrameLayout.LayoutParams(
                dp(330),
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                topMargin = dp(70)
                rightMargin = dp(14)
            }
        )

        setContentView(root)

        webView.loadUrl("https://speakirpg.overture.io.kr/")
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(): WebView {
        return WebView(this).apply {
            setBackgroundColor(Color.BLACK)

            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true

            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true

            settings.setSupportZoom(false)
            settings.builtInZoomControls = false
            settings.displayZoomControls = false

            settings.mediaPlaybackRequiresUserGesture = false

            // Desktop UA because SpeakiRPG is desktop-first.
            settings.userAgentString =
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                        "AppleWebKit/537.36 (KHTML, like Gecko) " +
                        "Chrome/126.0.0.0 Safari/537.36"

            webChromeClient = WebChromeClient()

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)

                    installMobileMouseBridge()
                }
            }

            isFocusable = true
            isFocusableInTouchMode = true
        }
    }

    /**
     * Mobile controls for a desktop mouse-oriented game.
     *
     * World:
     * 1 finger drag = held LMB drag
     * 2 finger drag = held RMB drag / camera
     * pinch = mouse wheel / camera zoom
     *
     * Scrollable menus:
     * 1 finger vertical drag = normal scrolling
     *
     * This keeps petting / cheek dragging while allowing Settings,
     * Ranking and other modal lists to scroll.
     */
    private fun installMobileMouseBridge() {
        val js = """
            (() => {
              if (window.__speakiMobileMouseBridgeInstalled) {
                return;
              }

              window.__speakiMobileMouseBridgeInstalled = true;

              let mode = null;

              let leftTarget = null;
              let rightTarget = null;
              let scrollTarget = null;
              let scrollStartTarget = null;

              let startX = 0;
              let startY = 0;
              let lastTouchY = 0;

              let moved = false;
              let lastPinchDistance = 0;

              const pointTarget = (x, y) => {
                return (
                  document.elementFromPoint(x, y) ||
                  document.body ||
                  document.documentElement
                );
              };

              const findScrollable = (element) => {
                let el = element;

                while (
                  el &&
                  el !== document.body &&
                  el !== document.documentElement
                ) {
                  const style = getComputedStyle(el);

                  const hasScrollableHeight =
                    el.scrollHeight > el.clientHeight + 2;

                  const overflowAllowsScroll =
                    style.overflowY === 'auto' ||
                    style.overflowY === 'scroll';

                  if (hasScrollableHeight && overflowAllowsScroll) {
                    return el;
                  }

                  el = el.parentElement;
                }

                return null;
              };

              const mouse = (
                type,
                target,
                x,
                y,
                button,
                buttons
              ) => {
                if (!target) {
                  target = pointTarget(x, y);
                }

                const ev = new MouseEvent(type, {
                  bubbles: true,
                  cancelable: true,
                  view: window,

                  clientX: x,
                  clientY: y,

                  screenX: x,
                  screenY: y,

                  button: button,
                  buttons: buttons
                });

                target.dispatchEvent(ev);
              };

              const pointer = (
                type,
                target,
                x,
                y,
                button,
                buttons,
                pointerId = 1
              ) => {
                if (!window.PointerEvent) {
                  return;
                }

                if (!target) {
                  target = pointTarget(x, y);
                }

                const ev = new PointerEvent(type, {
                  bubbles: true,
                  cancelable: true,

                  pointerId: pointerId,
                  pointerType: 'mouse',
                  isPrimary: true,

                  clientX: x,
                  clientY: y,

                  button: button,
                  buttons: buttons,

                  pressure: buttons ? 0.5 : 0
                });

                target.dispatchEvent(ev);
              };

              const down = (
                target,
                x,
                y,
                button,
                buttons
              ) => {
                pointer(
                  'pointerdown',
                  target,
                  x,
                  y,
                  button,
                  buttons
                );

                mouse(
                  'mousedown',
                  target,
                  x,
                  y,
                  button,
                  buttons
                );
              };

              const move = (
                target,
                x,
                y,
                button,
                buttons
              ) => {
                pointer(
                  'pointermove',
                  target,
                  x,
                  y,
                  button,
                  buttons
                );

                mouse(
                  'mousemove',
                  target,
                  x,
                  y,
                  button,
                  buttons
                );
              };

              const up = (
                target,
                x,
                y,
                button
              ) => {
                pointer(
                  'pointerup',
                  target,
                  x,
                  y,
                  button,
                  0
                );

                mouse(
                  'mouseup',
                  target,
                  x,
                  y,
                  button,
                  0
                );
              };

              const cancelLeft = (x, y) => {
                if (
                  mode === 'left' &&
                  leftTarget
                ) {
                  up(
                    pointTarget(x, y),
                    x,
                    y,
                    0
                  );
                }

                leftTarget = null;
              };

              const distance = (a, b) => {
                const dx = a.clientX - b.clientX;
                const dy = a.clientY - b.clientY;

                return Math.sqrt(
                  dx * dx +
                  dy * dy
                );
              };

              const center = (a, b) => {
                return {
                  x: (a.clientX + b.clientX) / 2,
                  y: (a.clientY + b.clientY) / 2
                };
              };

              document.addEventListener(
                'contextmenu',
                (e) => {
                  e.preventDefault();
                },
                true
              );

              document.addEventListener(
                'touchstart',
                (e) => {
                  if (
                    !e.touches ||
                    e.touches.length === 0
                  ) {
                    return;
                  }

                  // ONE FINGER
                  if (e.touches.length === 1) {
                    const t = e.touches[0];

                    const target = pointTarget(
                      t.clientX,
                      t.clientY
                    );

                    // Text fields/chat must receive the REAL Android touch.
                    // If we preventDefault() here and synthesize mouse events,
                    // WebView may focus the field but never open the soft keyboard.
                    if (
                      target &&
                      (
                        target.tagName === 'INPUT' ||
                        target.tagName === 'TEXTAREA' ||
                        target.isContentEditable
                      )
                    ) {
                      mode = 'native';
                      return;
                    }

                    const scrollable =
                      findScrollable(target);

                    // Inside a scrollable menu we first wait to see whether
                    // this is a tap or an actual drag.
                    //
                    // Short tap  -> click the original button/control.
                    // Move > 8px -> scroll the container.
                    if (scrollable) {
                      mode = 'scroll-pending';

                      scrollTarget =
                        scrollable;

                      scrollStartTarget =
                        target;

                      startX =
                        t.clientX;

                      startY =
                        t.clientY;

                      lastTouchY =
                        t.clientY;

                      moved = false;

                      e.preventDefault();
                      return;
                    }

                    mode = 'left';

                    moved = false;

                    startX =
                      t.clientX;

                    startY =
                      t.clientY;

                    leftTarget =
                      target;

                    // Helps text fields.
                    if (
                      leftTarget &&
                      (
                        leftTarget.tagName === 'INPUT' ||
                        leftTarget.tagName === 'TEXTAREA' ||
                        leftTarget.isContentEditable
                      )
                    ) {
                      try {
                        leftTarget.focus();
                      } catch (_) {}
                    }

                    down(
                      leftTarget,
                      t.clientX,
                      t.clientY,
                      0,
                      1
                    );

                    e.preventDefault();
                    return;
                  }

                  // TWO FINGERS:
                  // change to RMB camera mode.
                  const a = e.touches[0];
                  const b = e.touches[1];

                  const c =
                    center(a, b);

                  cancelLeft(
                    c.x,
                    c.y
                  );

                  scrollTarget = null;
                  scrollStartTarget = null;

                  mode = 'right';

                  rightTarget =
                    pointTarget(
                      c.x,
                      c.y
                    );

                  lastPinchDistance =
                    distance(a, b);

                  down(
                    rightTarget,
                    c.x,
                    c.y,
                    2,
                    2
                  );

                  e.preventDefault();
                },
                {
                  capture: true,
                  passive: false
                }
              );

              document.addEventListener(
                'touchmove',
                (e) => {
                  if (
                    !e.touches ||
                    e.touches.length === 0
                  ) {
                    return;
                  }

                  // SCROLLABLE HTML MENU:
                  // distinguish tap from scroll by movement threshold.
                  if (
                    (mode === 'scroll-pending' || mode === 'scroll') &&
                    e.touches.length === 1
                  ) {
                    const t =
                      e.touches[0];

                    const dx =
                      t.clientX - startX;

                    const dyFromStart =
                      t.clientY - startY;

                    const distanceFromStart =
                      Math.hypot(dx, dyFromStart);

                    if (
                      mode === 'scroll-pending' &&
                      distanceFromStart > 8
                    ) {
                      mode = 'scroll';
                      moved = true;
                    }

                    if (mode === 'scroll') {
                      const dy =
                        lastTouchY -
                        t.clientY;

                      if (scrollTarget) {
                        scrollTarget.scrollTop +=
                          dy;
                      }

                      lastTouchY =
                        t.clientY;
                    }

                    e.preventDefault();
                    return;
                  }

                  // LMB DRAG
                  if (
                    mode === 'left' &&
                    e.touches.length === 1
                  ) {
                    const t =
                      e.touches[0];

                    if (
                      Math.hypot(
                        t.clientX - startX,
                        t.clientY - startY
                      ) > 6
                    ) {
                      moved = true;
                    }

                    move(
                      pointTarget(
                        t.clientX,
                        t.clientY
                      ),
                      t.clientX,
                      t.clientY,
                      0,
                      1
                    );

                    e.preventDefault();
                    return;
                  }

                  // RMB DRAG + PINCH ZOOM
                  if (e.touches.length >= 2) {
                    const a = e.touches[0];
                    const b = e.touches[1];

                    const c =
                      center(a, b);

                    if (mode !== 'right') {
                      cancelLeft(
                        c.x,
                        c.y
                      );

                      scrollTarget = null;
                      scrollStartTarget = null;

                      mode = 'right';

                      rightTarget =
                        pointTarget(
                          c.x,
                          c.y
                        );

                      down(
                        rightTarget,
                        c.x,
                        c.y,
                        2,
                        2
                      );
                    }

                    // Camera movement.
                    move(
                      pointTarget(
                        c.x,
                        c.y
                      ),
                      c.x,
                      c.y,
                      2,
                      2
                    );

                    // Pinch -> wheel.
                    const currentDistance =
                      distance(a, b);

                    if (lastPinchDistance > 0) {
                      const diff =
                        currentDistance -
                        lastPinchDistance;

                      if (Math.abs(diff) > 1.5) {
                        const wheelTarget =
                          pointTarget(
                            c.x,
                            c.y
                          );

                        wheelTarget.dispatchEvent(
                          new WheelEvent(
                            'wheel',
                            {
                              bubbles: true,
                              cancelable: true,

                              clientX: c.x,
                              clientY: c.y,

                              deltaY:
                                -diff * 4,

                              deltaMode: 0
                            }
                          )
                        );
                      }
                    }

                    lastPinchDistance =
                      currentDistance;

                    e.preventDefault();
                  }
                },
                {
                  capture: true,
                  passive: false
                }
              );

              const finishTouch = (e) => {
                // Native text-field touch must remain completely untouched,
                // including touchend, so Android can show/manage the keyboard.
                if (mode === 'native') {
                  mode = null;
                  return;
                }

                const t =
                  e.changedTouches &&
                  e.changedTouches[0];

                const x =
                  t ? t.clientX : startX;

                const y =
                  t ? t.clientY : startY;

                if (mode === 'scroll-pending') {
                  // Finger never moved enough to become a scroll:
                  // treat it as a normal button tap.
                  const target =
                    scrollStartTarget ||
                    pointTarget(x, y);

                  down(
                    target,
                    x,
                    y,
                    0,
                    1
                  );

                  up(
                    target,
                    x,
                    y,
                    0
                  );

                  mouse(
                    'click',
                    target,
                    x,
                    y,
                    0,
                    0
                  );

                  scrollTarget = null;
                  scrollStartTarget = null;
                }

                else if (mode === 'scroll') {
                  scrollTarget = null;
                  scrollStartTarget = null;
                }

                else if (mode === 'left') {
                  const target =
                    pointTarget(x, y);

                  up(
                    target,
                    x,
                    y,
                    0
                  );

                  // Generate click only if it was actually a tap,
                  // not a cheek/petting drag.
                  if (!moved) {
                    mouse(
                      'click',
                      target,
                      x,
                      y,
                      0,
                      0
                    );
                  }
                }

                else if (mode === 'right') {
                  up(
                    pointTarget(x, y),
                    x,
                    y,
                    2
                  );
                }

                mode = null;

                leftTarget = null;
                rightTarget = null;
                scrollTarget = null;
                scrollStartTarget = null;

                lastPinchDistance = 0;
                moved = false;

                e.preventDefault();
              };

              document.addEventListener(
                'touchend',
                finishTouch,
                {
                  capture: true,
                  passive: false
                }
              );

              document.addEventListener(
                'touchcancel',
                finishTouch,
                {
                  capture: true,
                  passive: false
                }
              );

              console.log(
                '[Speaki Mobile] touch/mouse/scroll bridge installed'
              );
            })();
        """.trimIndent()

        webView.evaluateJavascript(
            js,
            null
        )
    }

    private fun buildUtilityPanel(): GridLayout {
        val panel = GridLayout(this).apply {
            columnCount = 4
            rowCount = 4

            setPadding(
                dp(8),
                dp(8),
                dp(8),
                dp(8)
            )

            setBackgroundColor(
                0xBB111111.toInt()
            )

            alpha = 0.92f
        }

        val keys = listOf(
            "Portal\nF" to WebKey("f", "KeyF"),
            "Emote\nT" to WebKey("t", "KeyT"),
            "Respawn\nR" to WebKey("r", "KeyR"),
            "Jump\nG" to WebKey("g", "KeyG"),

            "Camera\nC" to WebKey("c", "KeyC"),
            "Equip\nE" to WebKey("e", "KeyE"),
            "Stats\nU" to WebKey("u", "KeyU"),
            "Inventory\nI" to WebKey("i", "KeyI"),

            "Quests\nQ" to WebKey("q", "KeyQ"),
            "Mail\nM" to WebKey("m", "KeyM"),
            "Ranking\nK" to WebKey("k", "KeyK"),
            "Attend\nJ" to WebKey("j", "KeyJ"),

            "Settings\nO" to WebKey("o", "KeyO"),
            "Channel\nH" to WebKey("h", "KeyH")
        )

        keys.forEach { (label, key) ->
            panel.addView(
                makeTapButton(
                    label,
                    key
                ),
                GridLayout.LayoutParams().apply {
                    width = dp(76)
                    height = dp(58)

                    setMargins(
                        dp(2),
                        dp(2),
                        dp(2),
                        dp(2)
                    )
                }
            )
        }

        return panel
    }

    private fun makeTapButton(
        label: String,
        key: WebKey
    ): Button {
        return Button(this).apply {
            text = label
            textSize = 10f

            setTextColor(Color.WHITE)

            isAllCaps = false

            setPadding(
                0,
                0,
                0,
                0
            )

            setBackgroundColor(
                0xAA333333.toInt()
            )

            setOnClickListener {
                performHapticFeedback(
                    HapticFeedbackConstants.KEYBOARD_TAP
                )

                tapKey(key)

                utilityPanel.visibility =
                    View.GONE
            }
        }
    }

    private fun makeHoldButton(
        label: String,
        key: WebKey
    ): Button {
        return Button(this).apply {
            text = label

            setTextColor(Color.WHITE)

            isAllCaps = false

            setBackgroundColor(
                0x99000000.toInt()
            )

            setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        view.performHapticFeedback(
                            HapticFeedbackConstants.KEYBOARD_TAP
                        )

                        sendKey(
                            key,
                            down = true
                        )

                        true
                    }

                    MotionEvent.ACTION_UP,
                    MotionEvent.ACTION_CANCEL -> {
                        sendKey(
                            key,
                            down = false
                        )

                        true
                    }

                    else -> true
                }
            }
        }
    }

    private fun tapKey(key: WebKey) {
        sendKey(
            key,
            true
        )

        sendKey(
            key,
            false
        )
    }

    private fun updateMovementKeys(
        pressed: Set<Direction>
    ) {
        val desired =
            mutableSetOf<WebKey>()

        if (Direction.UP in pressed) {
            desired +=
                WebKey(
                    "w",
                    "KeyW"
                )
        }

        if (Direction.DOWN in pressed) {
            desired +=
                WebKey(
                    "s",
                    "KeyS"
                )
        }

        if (Direction.LEFT in pressed) {
            desired +=
                WebKey(
                    "a",
                    "KeyA"
                )
        }

        if (Direction.RIGHT in pressed) {
            desired +=
                WebKey(
                    "d",
                    "KeyD"
                )
        }

        setHeldKeys(desired)
    }

    private fun setHeldKeys(
        desired: Set<WebKey>
    ) {
        val toRelease =
            heldKeys - desired

        val toPress =
            desired - heldKeys

        toRelease.forEach {
            sendKey(
                it,
                false
            )
        }

        toPress.forEach {
            sendKey(
                it,
                true
            )
        }

        heldKeys.clear()
        heldKeys.addAll(desired)
    }

    /**
     * SINGLE dispatch.
     *
     * Do not dispatch once to window and again to document:
     * that can cause toggle-style menus to immediately open and close.
     */
    private fun sendKey(
        key: WebKey,
        down: Boolean
    ) {
        val eventType =
            if (down) {
                "keydown"
            } else {
                "keyup"
            }

        val js = """
            (() => {
              const target =
                document.activeElement ||
                document.body ||
                document.documentElement;

              const e = new KeyboardEvent(
                '$eventType',
                {
                  key: ${jsString(key.key)},
                  code: ${jsString(key.code)},
                  bubbles: true,
                  cancelable: true
                }
              );

              target.dispatchEvent(e);
            })();
        """.trimIndent()

        webView.evaluateJavascript(
            js,
            null
        )
    }

    private fun jsString(
        value: String
    ): String {
        val escaped =
            value
                .replace(
                    "\\",
                    "\\\\"
                )
                .replace(
                    "'",
                    "\\'"
                )
                .replace(
                    "\n",
                    "\\n"
                )
                .replace(
                    "\r",
                    "\\r"
                )

        return "'$escaped'"
    }

    private fun hideSystemUi() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }

    override fun onWindowFocusChanged(
        hasFocus: Boolean
    ) {
        super.onWindowFocusChanged(
            hasFocus
        )

        if (hasFocus) {
            hideSystemUi()
        }
    }

    override fun onPause() {
        setHeldKeys(
            emptySet()
        )

        super.onPause()

        webView.onPause()
    }

    override fun onResume() {
        super.onResume()

        webView.onResume()

        hideSystemUi()
    }

    override fun onDestroy() {
        setHeldKeys(
            emptySet()
        )

        webView.destroy()

        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (
            ::utilityPanel.isInitialized &&
            utilityPanel.visibility == View.VISIBLE
        ) {
            utilityPanel.visibility =
                View.GONE

            return
        }

        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    private fun dp(
        value: Int
    ): Int {
        return (
                value *
                        resources.displayMetrics.density
                ).toInt()
    }
}

data class WebKey(
    val key: String,
    val code: String
)

enum class Direction {
    UP,
    DOWN,
    LEFT,
    RIGHT
}
