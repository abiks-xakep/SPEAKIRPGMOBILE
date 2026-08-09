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
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var root: FrameLayout
    private lateinit var joystick: JoystickView
    private lateinit var attackButton: Button
    private lateinit var menuButton: Button
    private lateinit var utilityPanel: GridLayout
    private lateinit var editPanel: LinearLayout
    private lateinit var editLabel: TextView

    private val heldKeys = mutableSetOf<WebKey>()

    private val hudPrefs by lazy {
        getSharedPreferences("hud_layout", MODE_PRIVATE)
    }

    private var editMode = false
    private var selectedHudView: View? = null
    private var selectedHudKey: String? = null
    private var dragStartRawX = 0f
    private var dragStartRawY = 0f
    private var dragStartX = 0f
    private var dragStartY = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideSystemUi()

        root = FrameLayout(this).apply {
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
            FrameLayout.LayoutParams(dp(138), dp(138))
        )

        restoreHudView(
            joystick,
            "joystick",
            defaultX = dp(18).toFloat(),
            defaultYFromBottom = dp(112).toFloat(),
            defaultAlpha = 0.70f
        )

        joystick.setOnTouchListener { view, event ->
            if (editMode) {
                handleHudEditTouch(view, "joystick", event)
            } else {
                false
            }
        }

        // Permanent SPACE button for basic/auto attack.
        attackButton = Button(this).apply {
            text = "⚔\nSPACE"
            textSize = 13f
            setTextColor(Color.WHITE)
            isAllCaps = false
            setBackgroundColor(0x99000000.toInt())
            alpha = 0.78f
        }

        root.addView(
            attackButton,
            FrameLayout.LayoutParams(dp(88), dp(88))
        )

        restoreHudView(
            attackButton,
            "space",
            defaultXFromRight = dp(20).toFloat(),
            defaultYFraction = 0.52f,
            defaultAlpha = 0.78f
        )

        attackButton.setOnTouchListener { view, event ->
            if (editMode) {
                handleHudEditTouch(view, "space", event)
            } else {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        sendKey(WebKey(" ", "Space"), true)
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        sendKey(WebKey(" ", "Space"), false)
                        true
                    }
                    else -> true
                }
            }
        }

        // Hamburger menu.
        menuButton = Button(this).apply {
            text = "☰"
            textSize = 21f
            setTextColor(Color.WHITE)
            setBackgroundColor(0x99000000.toInt())
            alpha = 0.82f

            setOnClickListener {
                if (!editMode) {
                    utilityPanel.visibility =
                        if (utilityPanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                }
            }
        }

        root.addView(
            menuButton,
            FrameLayout.LayoutParams(dp(56), dp(52))
        )

        restoreHudView(
            menuButton,
            "menu",
            defaultXFromRight = dp(14).toFloat(),
            defaultY = dp(12).toFloat(),
            defaultAlpha = 0.82f
        )

        menuButton.setOnTouchListener { view, event ->
            if (editMode) handleHudEditTouch(view, "menu", event) else false
        }

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

        editPanel = buildHudEditPanel()
        editPanel.visibility = View.GONE
        root.addView(
            editPanel,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                topMargin = dp(10)
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

        val editHudButton = Button(this).apply {
            text = "Edit\nHUD"
            textSize = 10f
            setTextColor(Color.WHITE)
            isAllCaps = false
            setPadding(0, 0, 0, 0)
            setBackgroundColor(0xAA555555.toInt())
            setOnClickListener {
                utilityPanel.visibility = View.GONE
                enterHudEditMode()
            }
        }

        panel.addView(
            editHudButton,
            GridLayout.LayoutParams().apply {
                width = dp(76)
                height = dp(58)
                setMargins(dp(2), dp(2), dp(2), dp(2))
            }
        )

        return panel
    }

    private fun buildHudEditPanel(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(5), dp(4), dp(5), dp(4))
            setBackgroundColor(0xDD111111.toInt())

            editLabel = TextView(this@MainActivity).apply {
                text = "Tap a control"
                setTextColor(Color.WHITE)
                textSize = 11f
                gravity = Gravity.CENTER
            }
            addView(editLabel, LinearLayout.LayoutParams(dp(95), dp(42)))

            addView(hudEditorButton("−") { resizeSelectedHud(0.90f) })
            addView(hudEditorButton("+") { resizeSelectedHud(1.10f) })
            addView(hudEditorButton("α−") { changeSelectedHudAlpha(-0.10f) })
            addView(hudEditorButton("α+") { changeSelectedHudAlpha(0.10f) })
            addView(hudEditorButton("Reset", 64) { resetHudLayout() })
            addView(hudEditorButton("Done", 60) { exitHudEditMode() })
        }
    }

    private fun hudEditorButton(label: String, widthDp: Int = 46, action: () -> Unit): Button {
        return Button(this).apply {
            text = label
            textSize = 10f
            setTextColor(Color.WHITE)
            isAllCaps = false
            setPadding(0, 0, 0, 0)
            setBackgroundColor(0xAA333333.toInt())
            setOnClickListener { action() }
            layoutParams = LinearLayout.LayoutParams(dp(widthDp), dp(42))
        }
    }

    private fun enterHudEditMode() {
        editMode = true
        selectedHudView = null
        selectedHudKey = null
        setHeldKeys(emptySet())
        editLabel.text = "Tap a control"
        editPanel.visibility = View.VISIBLE
    }

    private fun exitHudEditMode() {
        saveHudView(joystick, "joystick")
        saveHudView(attackButton, "space")
        saveHudView(menuButton, "menu")
        editMode = false
        selectedHudView = null
        selectedHudKey = null
        editPanel.visibility = View.GONE
    }

    private fun handleHudEditTouch(view: View, key: String, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                selectedHudView = view
                selectedHudKey = key
                editLabel.text = when (key) {
                    "joystick" -> "Joystick"
                    "space" -> "SPACE"
                    "menu" -> "Menu ☰"
                    else -> key
                }
                dragStartRawX = event.rawX
                dragStartRawY = event.rawY
                dragStartX = view.x
                dragStartY = view.y
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val x = dragStartX + (event.rawX - dragStartRawX)
                val y = dragStartY + (event.rawY - dragStartRawY)
                val maxX = (root.width - view.width).coerceAtLeast(0).toFloat()
                val maxY = (root.height - view.height).coerceAtLeast(0).toFloat()
                view.x = x.coerceIn(0f, maxX)
                view.y = y.coerceIn(0f, maxY)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                saveHudView(view, key)
                return true
            }
        }
        return true
    }

    private fun resizeSelectedHud(factor: Float) {
        val view = selectedHudView ?: return
        val key = selectedHudKey ?: return
        if (view.width <= 0 || view.height <= 0) return

        val minWidth = when (key) {
            "joystick" -> dp(70)
            "space" -> dp(48)
            "menu" -> dp(40)
            else -> dp(40)
        }
        val maxWidth = when (key) {
            "joystick" -> dp(240)
            "space" -> dp(170)
            "menu" -> dp(110)
            else -> dp(200)
        }
        val ratio = view.height.toFloat() / view.width.toFloat()
        val newWidth = (view.width * factor).toInt().coerceIn(minWidth, maxWidth)
        val newHeight = (newWidth * ratio).toInt().coerceAtLeast(dp(36))

        view.layoutParams = view.layoutParams.apply {
            width = newWidth
            height = newHeight
        }
        view.requestLayout()
        view.post {
            clampHudView(view)
            saveHudView(view, key)
        }
    }

    private fun changeSelectedHudAlpha(delta: Float) {
        val view = selectedHudView ?: return
        val key = selectedHudKey ?: return
        view.alpha = (view.alpha + delta).coerceIn(0.20f, 1.00f)
        saveHudView(view, key)
    }

    private fun saveHudView(view: View, key: String) {
        if (view.width <= 0 || view.height <= 0) return
        hudPrefs.edit()
            .putFloat("${key}_x", view.x)
            .putFloat("${key}_y", view.y)
            .putInt("${key}_w", view.width)
            .putInt("${key}_h", view.height)
            .putFloat("${key}_alpha", view.alpha)
            .apply()
    }

    private fun restoreHudView(
        view: View,
        key: String,
        defaultX: Float? = null,
        defaultY: Float? = null,
        defaultXFromRight: Float? = null,
        defaultYFromBottom: Float? = null,
        defaultYFraction: Float? = null,
        defaultAlpha: Float
    ) {
        view.post {
            val w = hudPrefs.getInt("${key}_w", view.width)
            val h = hudPrefs.getInt("${key}_h", view.height)
            if (w > 0 && h > 0) {
                view.layoutParams = view.layoutParams.apply {
                    width = w
                    height = h
                }
                view.requestLayout()
            }
            view.post {
                val fallbackX = when {
                    defaultX != null -> defaultX
                    defaultXFromRight != null -> root.width - view.width - defaultXFromRight
                    else -> 0f
                }
                val fallbackY = when {
                    defaultY != null -> defaultY
                    defaultYFromBottom != null -> root.height - view.height - defaultYFromBottom
                    defaultYFraction != null -> (root.height - view.height) * defaultYFraction
                    else -> 0f
                }
                view.x = hudPrefs.getFloat("${key}_x", fallbackX)
                view.y = hudPrefs.getFloat("${key}_y", fallbackY)
                view.alpha = hudPrefs.getFloat("${key}_alpha", defaultAlpha)
                clampHudView(view)
            }
        }
    }

    private fun clampHudView(view: View) {
        val maxX = (root.width - view.width).coerceAtLeast(0).toFloat()
        val maxY = (root.height - view.height).coerceAtLeast(0).toFloat()
        view.x = view.x.coerceIn(0f, maxX)
        view.y = view.y.coerceIn(0f, maxY)
    }

    private fun resetHudLayout() {
        hudPrefs.edit().clear().apply()

        joystick.layoutParams = joystick.layoutParams.apply {
            width = dp(138); height = dp(138)
        }
        attackButton.layoutParams = attackButton.layoutParams.apply {
            width = dp(88); height = dp(88)
        }
        menuButton.layoutParams = menuButton.layoutParams.apply {
            width = dp(56); height = dp(52)
        }
        joystick.alpha = 0.70f
        attackButton.alpha = 0.78f
        menuButton.alpha = 0.82f
        joystick.requestLayout()
        attackButton.requestLayout()
        menuButton.requestLayout()

        root.post {
            joystick.x = dp(18).toFloat()
            joystick.y = (root.height - joystick.height - dp(112)).toFloat()
            attackButton.x = (root.width - attackButton.width - dp(20)).toFloat()
            attackButton.y = (root.height - attackButton.height) * 0.52f
            menuButton.x = (root.width - menuButton.width - dp(14)).toFloat()
            menuButton.y = dp(12).toFloat()
            selectedHudView = null
            selectedHudKey = null
            editLabel.text = "Tap a control"
        }
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
        if (editMode) {
            exitHudEditMode()
            return
        }

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
