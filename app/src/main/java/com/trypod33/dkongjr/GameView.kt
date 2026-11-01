package com.trypod33.dkongjr

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.media.AudioAttributes
import android.media.SoundPool
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

class GameView(context: Context) : SurfaceView(context), SurfaceHolder.Callback, Runnable {

    companion object {
        const val VW = 224f
        const val VH = 256f
        val P_ORANGE = 0xFFFF9700.toInt()
        val P_ORANGE_D = 0xFFB84700.toInt()
        val P_VINE = 0xFF00B000.toInt()
        val P_CHAIN = 0xFFF29C14.toInt()
        val P_PINK = 0xFFF058FF.toInt()
        val P_BLUE = 0xFF0403FF.toInt()
        val P_BLUE_L = 0xFF38D7FF.toInt()
        val P_LOCK = 0xFFF4BA15.toInt()
        val P_CYAN = 0xFF00FFFF.toInt()
        val P_WHITE = 0xFFFEFCFF.toInt()
        val P_RED = 0xFFE8070A.toInt()
        val P_PURP = 0xFF7673FF.toInt()
        val P_GOLD = 0xFFF6DA18.toInt()
    }

    private var thread: Thread? = null
    @Volatile private var running = false

    private val prefs: SharedPreferences = context.getSharedPreferences("dkongjr", Context.MODE_PRIVATE)
    private var hiScore = prefs.getInt("hiscore", 0)

    private val BM = HashMap<String, Bitmap>()
    private val paint = Paint()
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dst = RectF()
    private val clip = RectF()

    private var soundPool: SoundPool? = null
    private val sndIds = HashMap<String, Int>()

    class Seg(val x0: Float, val x1: Float, val y: Float, val t: Int)
    class Vine(val x: Float, val yTop: Float, val yBot: Float, val t: Int) // 1 vine 2 chain 3 unused 4 dotted track
    class Elev(val x: Float, val yTop: Float, val yBot: Float, val spd: Float)
    class Tower(val x0: Float, val x1: Float, val yTop: Float, val yBot: Float)
    class FruitS(val x: Float, val y: Float, val kind: Int) // 0 cherry 1 orange 2 banana 3 apple
    class KeySpot(val x: Float, val y: Float)
    class LockS(val cx: Float)
    class EnemyS(val kind: Int, val p1: Float, val p2: Float, val p3: Float, val p4: Float)
    // 0 red jaw(row y, x0, x1, spd) 1 blue jaw(vineX or 999 roam, yTop, yBot, spd)
    // 2 bird(baseY, amp, spd) 3 purple bird(baseY, amp, spd) 4 spark(pathIdx)

    class Lv {
        var cageSc = 1f
        val segs = ArrayList<Seg>()
        val vines = ArrayList<Vine>()
        val elevs = ArrayList<Elev>()
        val towers = ArrayList<Tower>()
        val fruits = ArrayList<FruitS>()
        val keySpots = ArrayList<KeySpot>()
        val locks = ArrayList<LockS>()
        val enemySpecs = ArrayList<EnemyS>()
        val sparkPaths = ArrayList<List<FloatArray>>()
        val tracks = ArrayList<Vine>()
        var jrX = 16f; var jrY = 241f
        var marioX0 = 60f; var marioX1 = 200f; var marioY = 41f
        var cageX = 4f; var cageY = 3f
        var keySprite = "key_blue"; var keyCount = 1
        var insertX0 = 6f; var insertX1 = 44f; var insertY = 42f
        var decor = 0
        var num = 1
    }

    class En(val kind: Int) {
        var x = 0f; var y = 0f; var dir = 1f
        var a = 0f; var b = 0f; var c = 0f
        var b2 = 0f; var b3 = 0f; var y0 = 0f; var c1 = 0f
        var wpi = 0
        var path: List<FloatArray>? = null
        var timer = 0f
    }

    private var stageNum = 1
    private var loop = 0
    private var lv = Lv()
    private var keysTaken = IntArray(0)
    private var keysInserted = 0
    private var carrying = -1

    private var jrX = 16f; private var jrY = 241f
    private var jrVy = 0f; private var jrVx = 0f
    private var jrMode = 0 // 0 walk 1 climb 2 air 3 ride
    private var jrFace = 1
    private var jrAnim = 0f
    private var jrVine = -1
    private var jrElev = -1

    private var marioX = 120f; private var marioDir = 1f
    private var fruitsGot = BooleanArray(0)
    private val enemies = ArrayList<En>()
    private val elevPos = ArrayList<Float>()

    private var score = 0
    private var bonus = 5000
    private var lives = 3
    private var extraAt = 20000
    private var bonusTick = 0
    private var state = 0 // 0 title 1 ready 2 play 3 dying 4 clear 5 intermission 6 gameover
    private var stateT = 0
    private var blink = 0f
    private var tick = 0
    private var speedMul = 1f

    private val ptrZone = HashMap<Int, Int>()
    private val ptrAxis = HashMap<Int, Int>()
    private val ptrStart = HashMap<Int, FloatArray>()
    private val ptrCur = HashMap<Int, FloatArray>()
    private var tL = false; private var tR = false; private var tU = false; private var tD = false
    private var jumpQueued = false
    private val padRect = RectF()
    private var scale = 1f
    private var offX = 0f; private var offY = 0f

    init {
        holder.addCallback(this)
        isFocusable = true
        for (n in arrayOf("gird1","gird2","gird3","gird4","vine","chain","brick","cap1","cage",
            "key_blue","key_pink","key_white","mario","jr_idle","jr_run","jr_climb0","jr_climb1",
            "jaw_red","jaw_blue","bird_nit","bird_chain","spark_blue","elevcap",
            "fruit_cherry","fruit_orange","fruit_banana")) {
            try {
                val id = resources.getIdentifier(n, "drawable", context.packageName)
                if (id != 0) BM[n] = BitmapFactory.decodeResource(resources, id)
            } catch (e: Exception) {}
        }
        textPaint.typeface = Typeface.MONOSPACE
        textPaint.isFakeBoldText = true
        try {
            soundPool = SoundPool.Builder().setMaxStreams(4)
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
            for (n in arrayOf("jump","grab","insert","fruit","die","clear","intro")) {
                val id = resources.getIdentifier(n, "raw", context.packageName)
                if (id != 0) sndIds[n] = soundPool!!.load(context, id, 1)
            }
        } catch (e: Exception) {}
        buildStage(1)
    }

    private fun snd(n: String) {
        try { sndIds[n]?.let { soundPool!!.play(it, 0.9f, 0.9f, 1, 0, 1f) } } catch (e: Exception) {}
    }

    private fun buildStage(n: Int) {
        lv = Lv(); lv.num = n
        keysTaken = IntArray(0); keysInserted = 0; carrying = -1
        enemies.clear(); elevPos.clear(); fruitsGot = BooleanArray(0)
        if (n == 1) {
            lv.keySprite = "key_blue"; lv.keyCount = 4
            lv.segs.add(Seg(0f, 127f, 72f, 1)); lv.segs.add(Seg(128f, 191f, 80f, 1))
            lv.segs.add(Seg(52f, 79f, 44f, 1))
            lv.segs.add(Seg(32f, 63f, 120f, 1)); lv.segs.add(Seg(176f, 223f, 144f, 1))
            lv.segs.add(Seg(32f, 79f, 168f, 1))
            lv.segs.add(Seg(0f, 223f, 248f, 5))
            lv.vines.add(Vine(2f, 80f, 223f, 1)); lv.vines.add(Vine(24f, 80f, 215f, 1))
            lv.vines.add(Vine(48f, 124f, 223f, 1)); lv.vines.add(Vine(90f, 32f, 199f, 1))
            lv.vines.add(Vine(120f, 80f, 151f, 1)); lv.vines.add(Vine(144f, 84f, 199f, 1))
            lv.keySpots.add(KeySpot(83f, 96f)); lv.keySpots.add(KeySpot(115f, 88f))
            lv.keySpots.add(KeySpot(139f, 140f)); lv.keySpots.add(KeySpot(43f, 150f))
            lv.fruits.add(FruitS(130f, 110f, 2)); lv.fruits.add(FruitS(180f, 145f, 0))
            lv.fruits.add(FruitS(60f, 150f, 2)); lv.fruits.add(FruitS(150f, 190f, 1))
            lv.fruits.add(FruitS(100f, 230f, 2))
            lv.enemySpecs.add(EnemyS(0, 120f, 32f, 63f, 0.5f))
            lv.enemySpecs.add(EnemyS(0, 144f, 176f, 223f, 0.55f))
            lv.enemySpecs.add(EnemyS(1, 90f, 90f, 190f, 0.4f))
            lv.enemySpecs.add(EnemyS(1, 122f, 90f, 140f, 0.45f))
            lv.enemySpecs.add(EnemyS(2, 100f, 14f, 0.55f, 0f))
            lv.jrX = 16f; lv.jrY = 248f
            lv.marioX0 = 52f; lv.marioX1 = 79f; lv.marioY = 40f
            lv.cageX = 6f; lv.cageY = 34f
            lv.insertX0 = 52f; lv.insertX1 = 76f; lv.insertY = 44f
        } else if (n == 2) {
            lv.keySprite = "key_pink"; lv.keyCount = 1
            lv.segs.add(Seg(0f, 151f, 73f, 2)); lv.segs.add(Seg(168f, 191f, 73f, 2))
            lv.segs.add(Seg(40f, 207f, 154f, 6))
            lv.segs.add(Seg(0f, 23f, 240f, 2)); lv.segs.add(Seg(80f, 103f, 240f, 2))
            lv.segs.add(Seg(184f, 223f, 240f, 2))
            lv.towers.add(Tower(0f, 23f, 178f, 240f)); lv.towers.add(Tower(200f, 215f, 176f, 240f))
            lv.vines.add(Vine(8f, 81f, 236f, 2))
            var cxx = 58f
            while (cxx <= 196f) { lv.vines.add(Vine(cxx, 81f, 154f, 2)); cxx += 23f }
            lv.keySpots.add(KeySpot(145f, 104f))
            lv.fruits.add(FruitS(10f, 105f, 2)); lv.fruits.add(FruitS(65f, 90f, 0))
            lv.fruits.add(FruitS(100f, 120f, 1)); lv.fruits.add(FruitS(44f, 165f, 2))
            lv.fruits.add(FruitS(92f, 165f, 2))
            lv.enemySpecs.add(EnemyS(0, 154f, 44f, 203f, 0.5f))
            lv.enemySpecs.add(EnemyS(1, 60f, 95f, 145f, 0.45f))
            lv.enemySpecs.add(EnemyS(1, 129f, 95f, 145f, 0.5f))
            lv.enemySpecs.add(EnemyS(2, 120f, 12f, 0.6f, 0f))
            lv.jrX = 8f; lv.jrY = 240f
            lv.marioX0 = 56f; lv.marioX1 = 79f; lv.marioY = 40f
            lv.cageX = 6f; lv.cageY = 34f
            lv.insertX0 = 56f; lv.insertX1 = 88f; lv.insertY = 73f
        } else if (n == 3) {
            lv.keySprite = "key_white"; lv.keyCount = 1
            lv.segs.add(Seg(48f, 79f, 40f, 3)); lv.segs.add(Seg(56f, 87f, 64f, 3))
            lv.segs.add(Seg(0f, 57f, 72f, 3)); lv.segs.add(Seg(86f, 223f, 72f, 3))
            lv.segs.add(Seg(40f, 183f, 128f, 3)); lv.segs.add(Seg(192f, 223f, 128f, 3))
            lv.segs.add(Seg(0f, 223f, 168f, 3))
            lv.segs.add(Seg(40f, 183f, 208f, 3))
            lv.segs.add(Seg(0f, 140f, 244f, 3))
            lv.elevs.add(Elev(8f, 76f, 244f, 0.5f)); lv.elevs.add(Elev(216f, 76f, 244f, 0.42f))
            lv.tracks.add(Vine(94f, 80f, 128f, 4)); lv.tracks.add(Vine(28f, 88f, 168f, 4))
            lv.tracks.add(Vine(198f, 88f, 168f, 4)); lv.tracks.add(Vine(46f, 136f, 208f, 4))
            lv.tracks.add(Vine(94f, 176f, 244f, 4))
            lv.keySpots.add(KeySpot(65f, 48f))
            lv.fruits.add(FruitS(120f, 64f, 1)); lv.fruits.add(FruitS(40f, 120f, 2))
            lv.fruits.add(FruitS(128f, 160f, 2)); lv.fruits.add(FruitS(64f, 200f, 0))
            lv.fruits.add(FruitS(180f, 120f, 1))
            lv.enemySpecs.add(EnemyS(2, 150f, 10f, 0.55f, 0f))
            lv.enemySpecs.add(EnemyS(3, 100f, 12f, -0.5f, 2f))
            lv.jrX = 12f; lv.jrY = 244f
            lv.marioX0 = 48f; lv.marioX1 = 79f; lv.marioY = 40f
            lv.cageX = 6f; lv.cageY = 34f
            lv.insertX0 = 0f; lv.insertX1 = 24f; lv.insertY = 244f
        } else {
            lv.keySprite = "key_blue"; lv.keyCount = 6
            lv.segs.add(Seg(8f, 215f, 72f, 4)); lv.segs.add(Seg(8f, 215f, 84f, 4))
            lv.segs.add(Seg(0f, 223f, 248f, 4))
            for (cc in floatArrayOf(27f, 75f, 99f, 123f, 147f, 195f)) {
                lv.vines.add(Vine(cc - 2f, 92f, 226f, 2)); lv.locks.add(LockS(cc))
                lv.keySpots.add(KeySpot(cc - 7f, 218f))
            }
            lv.tracks.add(Vine(46f, 92f, 240f, 4)); lv.tracks.add(Vine(166f, 92f, 240f, 4))
            lv.fruits.add(FruitS(48f, 220f, 0)); lv.fruits.add(FruitS(168f, 220f, 0))
            lv.enemySpecs.add(EnemyS(3, 100f, 10f, 0.55f, 0f))
            lv.enemySpecs.add(EnemyS(3, 140f, 12f, -0.5f, 2.5f))
            lv.enemySpecs.add(EnemyS(1, 75f, 100f, 220f, 0.5f))
            lv.enemySpecs.add(EnemyS(1, 123f, 100f, 220f, 0.55f))
            lv.enemySpecs.add(EnemyS(1, 999f, 100f, 220f, 0.5f))
            lv.jrX = 112f; lv.jrY = 248f
            lv.marioX0 = 20f; lv.marioX1 = 84f; lv.marioY = 72f
            lv.cageX = 90f; lv.cageY = 28f; lv.cageSc = 1.15f
            lv.insertX0 = 0f; lv.insertX1 = 0f; lv.insertY = 102f
        }
        keysTaken = IntArray(lv.keySpots.size)
        fruitsGot = BooleanArray(lv.fruits.size)
        marioX = (lv.marioX0 + lv.marioX1) / 2f; marioDir = 1f
        resetEnemies()
        jrX = lv.jrX; jrY = lv.jrY
        bonus = 5000
    }
    private fun resetEnemies() {
        enemies.clear(); elevPos.clear()
        for (s in lv.enemySpecs) {
            val e = En(s.kind)
            when (s.kind) {
                0 -> { e.y = s.p1; e.x = (s.p2 + s.p3) / 2f; e.a = s.p2; e.b = s.p3; e.c = s.p4; e.dir = 1f }
                1 -> { e.a = s.p1; e.x = if (s.p1 >= 900f) 60f else s.p1; e.y = s.p2; e.b = s.p3; e.b2 = s.p2; e.b3 = s.p3; e.c = s.p4; e.dir = 1f }
                2, 3 -> { e.y0 = s.p1; e.y = s.p1; e.a = s.p2; e.b = s.p3; e.c1 = s.p4 * 60f; e.x = if (s.p3 < 0) 240f else -16f }
                4 -> { e.path = lv.sparkPaths[s.p1.toInt()]; e.wpi = 0; val p0 = e.path!![0]; e.x = p0[0]; e.y = p0[1] }
            }
            enemies.add(e)
        }
        for (el in lv.elevs) elevPos.add(el.yBot)
    }

    // ---------- surface / loop ----------
    override fun run() {
        var last = System.nanoTime()
        var acc = 0.0
        while (running) {
            val now = System.nanoTime()
            acc += (now - last) / 1e9; last = now
            var steps = 0
            while (acc >= 1.0 / 60.0 && steps < 4) { update(); acc -= 1.0 / 60.0; steps++ }
            if (acc > 0.25) acc = 0.0
            val c: Canvas? = try { holder.lockCanvas() } catch (e: Exception) { null }
            if (c != null) {
                try { doDraw(c) } catch (e: Exception) {}
                try { holder.unlockCanvasAndPost(c) } catch (e: Exception) {}
            }
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) { if (thread == null || !(thread?.isAlive ?: false)) { running = true; thread = Thread(this); thread?.start() } }
    override fun surfaceChanged(holder: SurfaceHolder, f: Int, w: Int, h: Int) { computeGeom(w, h) }
    override fun surfaceDestroyed(holder: SurfaceHolder) {
        running = false
        try { thread?.join(500) } catch (e: Exception) {}
        thread = null
    }

    private fun computeGeom(w: Int, h: Int) {
        val sw = w.toFloat(); val sh = h.toFloat()
        scale = min(sw / VW, sh / (VH + 58f))
        offX = (sw - VW * scale) / 2f
        offY = (sh - (VH + 58f) * scale) / 2f
        padRect.set(offX + 8f * scale, offY + VH * scale + 4f * scale,
            offX + (VW - 8f) * scale, offY + (VH + 54f) * scale)
    }

    // ---------- input (stateless, per DK v0.1.12 lessons) ----------
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = ev.actionIndex
                val pid = ev.getPointerId(i)
                val zx = zoneOf(ev.getX(i), ev.getY(i))
                ptrZone[pid] = zx
                ptrAxis.remove(pid)
                ptrStart[pid] = floatArrayOf(ev.getX(i), ev.getY(i))
                ptrCur[pid] = floatArrayOf(ev.getX(i), ev.getY(i))
                if (zx == 14 || zx == 15) jumpQueued = true
                if (zx == 0 && state == 0) startGame()
                if (zx == 0 && state == 6) toTitle()
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until ev.pointerCount) {
                    val pid = ev.getPointerId(i)
                    if (!ptrZone.containsKey(pid)) continue
                    val z = zoneOf(ev.getX(i), ev.getY(i))
                    ptrZone[pid] = z
                    ptrCur[pid] = floatArrayOf(ev.getX(i), ev.getY(i))
                    if (z == 14 || z == 15) jumpQueued = true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val idx = if (ev.actionMasked == MotionEvent.ACTION_UP) -1 else ev.actionIndex
                val ids = ArrayList<Int>()
                for (k in ptrZone.keys) if (idx < 0 || ev.getPointerId(idx) == k) ids.add(k)
                for (k in ids) { ptrZone.remove(k); ptrAxis.remove(k); ptrStart.remove(k); ptrCur.remove(k) }
            }
            MotionEvent.ACTION_CANCEL -> { ptrZone.clear(); ptrAxis.clear(); ptrStart.clear(); ptrCur.clear() }
        }
        return true
    }

    private fun zoneOf(sx: Float, sy: Float): Int {
        if (sx >= padRect.left && sx <= padRect.right && sy >= padRect.top && sy <= padRect.bottom) {
            val fx = (sx - padRect.left) / padRect.width()
            val fy = (sy - padRect.top) / padRect.height()
            if (fx > 0.62f) return if (fx > 0.80f) 15 else 14
            val dx = fx / 0.62f
            if (dx > 0.28f && dx < 0.72f && (fy < 0.32f || fy > 0.68f)) return if (fy < 0.32f) 10 else 12
            if (fy > 0.30f && fy < 0.70f && (dx < 0.30f || dx > 0.70f)) return if (dx < 0.30f) 13 else 11
            return 9
        }
        return 0
    }

    private fun recomputeDirs() {
        var L = false; var R = false; var U = false; var D = false
        val arm = 46f * resources.displayMetrics.density
        for ((pid, z) in ptrZone) {
            val cur = ptrCur[pid] ?: continue
            if (z in 10..13) {
                when (z) { 10 -> U = true; 11 -> R = true; 12 -> D = true; 13 -> L = true }
                continue
            }
            if (z != 0) continue
            val st = ptrStart[pid] ?: continue
            val ax = cur[0] - st[0]; val ay = cur[1] - st[1]
            val mag = sqrt(ax * ax + ay * ay)
            if (mag < arm * 0.25f) continue
            var axis = ptrAxis[pid] ?: -1
            if (axis == -1) axis = if (abs(ay) > abs(ax) * 0.55f) 1 else 0
            else if (axis == 0 && abs(ay) > abs(ax) * 1.5f) axis = 1
            else if (axis == 1 && abs(ax) > abs(ay) * 0.4f) axis = 0
            ptrAxis[pid] = axis
            if (axis == 0) { if (ax > 0) R = true else L = true }
            else { if (ay > 0) D = true else U = true }
        }
        tL = L; tR = R; tU = U; tD = D
    }

    // ---------- helpers ----------
    private fun rowAt(x: Float, y: Float): Seg? {
        for (s in lv.segs) if (abs(s.y - y) <= 1.5f && x >= s.x0 - 1.5f && x <= s.x1 + 1.5f) return s
        return null
    }
    private fun segUnder(x: Float, y: Float): Seg? {
        for (s in lv.segs) if (abs(s.y - y) <= 2.5f && x >= s.x0 - 1f && x <= s.x1 + 1f) return s
        return null
    }
    private fun vineAt(x: Float, y: Float): Vine? {
        for (v in lv.vines) {
            if (abs(x - (v.x + 2f)) <= 7f && y >= v.yTop - 12f && y <= v.yBot + 6f) return v
        }
        return null
    }

    // ---------- update ----------
    private fun update() {
        tick++
        blink += 0.03f
        recomputeDirs()
        val sm = speedMul
        when (state) {
            0 -> {}
            1 -> { stateT--; if (stateT <= 0) state = 2 }
            2 -> updatePlay(sm)
            3 -> {
                stateT--; jrY += 1.1f
                if (stateT <= 0) {
                    lives--
                    if (lives < 0) { state = 6; saveHi() } else respawn()
                }
            }
            4 -> { stateT--; if (stateT <= 0) { state = 5; stateT = 0; score += bonus; bonus = 0 } }
            5 -> {
                stateT++
                lv.cageX += 1.1f; marioX += 1.5f
                if (stateT > 210) { loop++; stageNum = stageNum % 4 + 1; speedMul = 1f + 0.12f * loop; buildStage(stageNum); state = 1; stateT = 110; snd("intro") }
            }
            6 -> {}
        }
        jumpQueued = false
    }

    private fun saveHi() { if (score > hiScore) { hiScore = score; prefs.edit().putInt("hiscore", hiScore).apply() } }

    private fun startGame() {
        score = 0; lives = 3; extraAt = 20000; stageNum = 1; loop = 0; speedMul = 1f
        buildStage(1); state = 1; stateT = 110; snd("intro")
    }
    private fun toTitle() { saveHi(); score = 0; state = 0 }

    private fun respawn() {
        jrX = lv.jrX; jrY = lv.jrY; jrVy = 0f; jrVx = 0f; jrMode = 0; jrVine = -1; jrElev = -1
        if (carrying >= 0) keysTaken[carrying] = 0
        carrying = -1
        bonus = 5000
        resetEnemies()
        state = 2
    }

    private fun die() {
        if (state != 2) return
        state = 3; stateT = 80; snd("die")
        if (carrying >= 0) { keysTaken[carrying] = 0; carrying = -1 }
    }

    private fun grabKey(i: Int) {
        if (carrying >= 0 || keysTaken[i] != 0) return
        keysTaken[i] = 1; carrying = i; score += 100; snd("grab"); checkExtra()
    }

    private fun insertKey() {
        if (carrying < 0) return
        keysTaken[carrying] = 2; carrying = -1
        keysInserted++
        score += if (lv.num == 4) 800 else 500
        snd("insert"); checkExtra()
        if (keysInserted >= lv.keyCount) { state = 4; stateT = 100; snd("clear"); saveHi() }
    }

    private fun checkExtra() { if (score >= extraAt) { lives++; extraAt += 30000; snd("fruit") } }

    private fun updatePlay(sm: Float) {
        bonusTick++
        if (bonusTick >= 26) { bonusTick = 0; if (bonus > 0) bonus -= 100 }
        marioX += marioDir * 0.45f * sm
        if (marioX < lv.marioX0) { marioX = lv.marioX0; marioDir = 1f }
        if (marioX > lv.marioX1) { marioX = lv.marioX1; marioDir = -1f }
        for (i in lv.elevs.indices) {
            val el = lv.elevs[i]
            var p = elevPos[i] - el.spd * sm
            if (p < el.yTop) p = el.yBot
            elevPos[i] = p
        }
        for (e in enemies) updateEnemy(e, sm)
        for (i in lv.fruits.indices) {
            if (fruitsGot[i]) continue
            val f = lv.fruits[i]
            if (abs(jrX - f.x) < 9f && abs((jrY - 8f) - f.y) < 12f) {
                fruitsGot[i] = true
                score += intArrayOf(500, 300, 400, 200)[f.kind]; snd("fruit"); checkExtra()
            }
        }
        for (i in lv.keySpots.indices) {
            if (keysTaken[i] != 0) continue
            val k = lv.keySpots[i]
            val kcy = k.y + 8f
            if (abs(jrX - (k.x + 7f)) <= 9f && jrY >= kcy - 10f && jrY <= kcy + 22f) grabKey(i)
        }
        if (carrying >= 0) {
            if (lv.num == 4) {
                for (l in lv.locks) if (abs(jrX - l.cx) <= 8f && jrY <= lv.insertY) { insertKey(); break }
            } else if (jrX >= lv.insertX0 && jrX <= lv.insertX1 && jrY <= lv.insertY) insertKey()
        }
        updateJr(sm)
        if (state == 2) {
            val jx0 = jrX - 6f; val jx1 = jrX + 6f; val jy0 = jrY - 15f; val jy1 = jrY - 1f
            for (e in enemies) {
                var ex0 = e.x - 7f; var ex1 = e.x + 7f; var ey0 = e.y - 10f; var ey1 = e.y
                when (e.kind) {
                    2, 3 -> { ex0 = e.x - 8f; ex1 = e.x + 8f; ey0 = e.y - 6f; ey1 = e.y + 6f }
                    4 -> { ex0 = e.x - 6f; ex1 = e.x + 6f; ey0 = e.y - 6f; ey1 = e.y + 6f }
                }
                if (jx1 > ex0 && jx0 < ex1 && jy1 > ey0 && jy0 < ey1) { die(); break }
            }
        }
    }

    private fun updateEnemy(e: En, sm: Float) {
        when (e.kind) {
            0 -> {
                e.x += e.dir * e.c * sm
                if (e.x < e.a) { e.x = e.a; e.dir = 1f }
                if (e.x > e.b) { e.x = e.b; e.dir = -1f }
            }
            1 -> {
                if (e.a >= 900f) {
                    e.timer += 0.02f * sm
                    if (e.timer >= 6f) {
                        e.timer = 0f
                        val vs = lv.vines.filter { it.t == 2 }
                        if (vs.isNotEmpty()) {
                            val v = vs[(Math.random() * vs.size).toInt()]
                            e.x = v.x + 2f; e.b2 = v.yTop; e.b3 = v.yBot; e.dir = 1f; e.y = v.yTop
                        }
                    }
                    if (e.b3 > 0f) {
                        e.y += e.dir * e.c * sm
                        if (e.y > e.b3 - 40f) e.dir = -1f
                        if (e.y < e.b2 + 4f) e.dir = 1f
                    }
                } else {
                    e.y += e.dir * e.c * sm
                    if (e.y > e.b - 4f) { e.y = e.b - 4f; e.dir = -1f }
                    if (e.y < e.b2 + 4f) { e.y = e.b2 + 4f; e.dir = 1f }
                }
            }
            2, 3 -> {
                e.c1 += 1f
                e.x += e.b * sm
                e.y = e.y0 + sin(e.c1 / 22f) * e.a
                if (e.b < 0 && e.x < -20f) e.x = 244f
                if (e.b > 0 && e.x > 244f) e.x = -20f
            }
            4 -> {
                val path = e.path!!
                val wp = path[e.wpi]
                val dx = wp[0] - e.x; val dy = wp[1] - e.y
                val d = sqrt(dx * dx + dy * dy)
                val spd = 0.6f * sm
                if (d <= spd) { e.x = wp[0]; e.y = wp[1]; e.wpi = (e.wpi + 1) % path.size }
                else { e.x += dx / d * spd; e.y += dy / d * spd }
            }
        }
    }

    private fun updateJr(sm: Float) {
        if (jrMode == 1) {
            if (jrVine < 0 || jrVine >= lv.vines.size) { jrMode = 0; return }
            val v = lv.vines[jrVine]
            val dy = (if (tD) 1f else 0f) - (if (tU) 1f else 0f)
            jrY += dy * 0.9f * sm
            jrAnim += 0.14f * sm
            if (jrY < v.yTop) jrY = v.yTop
            if (jrY > v.yBot) jrY = v.yBot
            if (tU && jrY <= v.yTop + 0.6f) {
                val seg = lv.segs.firstOrNull { abs(it.y + 8f - v.yTop) <= 2.5f && jrX >= it.x0 - 2f && jrX <= it.x1 + 2f }
                if (seg != null) { jrY = seg.y; jrMode = 0; jrVine = -1; return }
            }
            if (tD && jrY >= v.yBot - 0.6f) {
                val seg = rowAt(jrX, v.yBot)
                if (seg != null) { jrY = seg.y; jrMode = 0; jrVine = -1; return }
            }
            if (jumpQueued) { jrMode = 2; jrVy = -3.2f; jrVx = jrFace * 1.2f; jrVine = -1; snd("jump") }
            return
        }
        if (jrMode == 3) {
            if (jrElev < 0 || jrElev >= lv.elevs.size) { jrMode = 0; return }
            val el = lv.elevs[jrElev]
            jrX = el.x; jrY = elevPos[jrElev]
            if (tL || tR) {
                for (s in lv.segs) {
                    val near = if (tR) (s.x0 >= el.x - 24f && s.x0 <= el.x + 24f) else (s.x1 >= el.x - 24f && s.x1 <= el.x + 24f)
                    if (near && abs(s.y - jrY) <= 5f) {
                        jrX = if (tR) s.x0 + 3f else s.x1 - 3f
                        jrY = s.y; jrMode = 0; jrElev = -1
                        return
                    }
                }
            }
            return
        }
        if (jrMode == 0) {
            var nx = jrX
            if (tL) { nx -= 1.1f * sm; jrFace = -1 }
            if (tR) { nx += 1.1f * sm; jrFace = 1 }
            jrAnim += 0.16f * sm
            val seg = segUnder(jrX, jrY)
            if (seg == null) {
                jrMode = 2; jrVy = 0.5f
                jrVx = if (tL) -0.8f else if (tR) 0.8f else 0f
            } else {
                if (nx < seg.x0 - 1f) nx = seg.x0 - 1f
                if (nx > seg.x1 + 1f) nx = seg.x1 + 1f
                jrX = nx
            }
            if (tU || tD) {
                val v = vineAt(jrX, jrY)
                if (v != null) { jrVine = lv.vines.indexOf(v); jrMode = 1; jrX = v.x + 2f; return }
            }
            if (jumpQueued) {
                jrMode = 2; jrVy = -3.4f
                jrVx = if (tL) -1.15f else if (tR) 1.15f else jrFace * 1.15f
                snd("jump")
            }
            for (i in lv.elevs.indices) {
                val el = lv.elevs[i]
                if (abs(jrX - el.x) <= 8f && abs(jrY - elevPos[i]) <= 5f) { jrElev = i; jrMode = 3; return }
            }
            return
        }
        // AIR
        jrVy += 0.18f * sm
        if (jrVy > 5f) jrVy = 5f
        val py = jrY
        jrY += jrVy * sm
        jrX += jrVx * sm
        if (jrX < 4f) { jrX = 4f; jrVx = 0f }
        if (jrX > 220f) { jrX = 220f; jrVx = 0f }
        if (jrVy > 0.4f) {
            val v = vineAt(jrX, jrY)
            if (v != null) { jrVine = lv.vines.indexOf(v); jrMode = 1; jrX = v.x + 2f; jrVy = 0f; return }
        }
        if (jrVy > 0f) {
            for (s in lv.segs) {
                if (jrX >= s.x0 - 1f && jrX <= s.x1 + 1f && py <= s.y + 0.6f && jrY >= s.y) {
                    jrY = s.y; jrMode = 0; jrVy = 0f; jrVx = 0f
                    if (lv.num == 2 && jrX >= 76f && jrX <= 114f) { jrMode = 2; jrVy = -3.8f; snd("jump") }
                    return
                }
            }
        }
        if (jrY > 252f) { jrY = 250f; die() }
        for (i in lv.elevs.indices) {
            val el = lv.elevs[i]
            if (abs(jrX - el.x) <= 8f && abs(jrY - elevPos[i]) <= 4f && jrVy > 0f) { jrElev = i; jrMode = 3; jrVy = 0f; return }
        }
    }

    // ---------- drawing ----------
    private fun doDraw(c: Canvas) {
        c.drawColor(Color.BLACK)
        c.save()
        c.translate(offX, offY)
        c.scale(scale, scale)
        if (state == 0) drawTitle(c) else drawGame(c)
        c.restore()
        drawPad(c)
    }

    private fun sprTL(c: Canvas, b: Bitmap?, x: Float, y: Float, sc: Float = 1f, flip: Boolean = false) {
        if (b == null) return
        val w = b.width / 8f * sc; val h = b.height / 8f * sc
        c.save()
        c.translate(x, y)
        if (flip) { c.scale(-1f, 1f); c.translate(-w, 0f) }
        dst.set(0f, 0f, w, h)
        c.drawBitmap(b, null, dst, paint)
        c.restore()
    }

    private fun spr(c: Canvas, name: String, cx: Float, feetY: Float, sc: Float = 1f, flip: Boolean = false) {
        val b = BM[name] ?: return
        val w = b.width / 8f * sc; val h = b.height / 8f * sc
        c.save()
        c.translate(cx, feetY)
        if (flip) c.scale(-1f, 1f)
        dst.set(-w / 2f, -h, w / 2f, 0f)
        c.drawBitmap(b, null, dst, paint)
        c.restore()
    }

    private fun rect(c: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, col: Int) {
        paint.color = col; paint.style = Paint.Style.FILL
        c.drawRect(x0, y0, x1, y1, paint)
    }

    private fun drawRow(c: Canvas, s: Seg) {
        if (s.t == 5 || s.t == 6) {
            val gb = BM[if (s.t == 5) "grass" else "chain_h"] ?: return
            var x = s.x0
            while (x < s.x1) { dst.set(x, s.y, x + 16f, s.y + 8f); c.drawBitmap(gb, null, dst, paint); x += 16f }
            return
        }
        val tile = when (s.t) { 1 -> "gird1"; 2 -> "gird2"; 3 -> "gird3"; else -> "gird4" }
        val b = BM[tile] ?: return
        c.save()
        clip.set(s.x0, s.y, s.x1, s.y + 8f)
        c.clipRect(clip)
        var x = s.x0
        while (x < s.x1) {
            dst.set(x, s.y, x + 16f, s.y + 8f)
            c.drawBitmap(b, null, dst, paint)
            x += 16f
        }
        c.restore()
    }

    private fun drawVine(c: Canvas, v: Vine, dotted: Boolean) {
        if (dotted) {
            paint.color = P_CYAN
            var y = v.yTop
            while (y < v.yBot) { c.drawRect(v.x, y, v.x + 2f, y + 2f, paint); y += 6f }
            return
        }
        when (v.t) {
            1 -> { val b = BM["vine"]; var y = v.yTop
                while (y < v.yBot) { sprTL(c, b, v.x - 2f, y); y += 16f } }
            2 -> { val b = BM["chain"]; var y = v.yTop
                while (y < v.yBot) { sprTL(c, b, v.x - 2.5f, y); y += 16f } }
            else -> {
                rect(c, v.x, v.yTop, v.x + 4f, v.yBot, P_BLUE)
                paint.color = P_WHITE
                var y = v.yTop + 2f
                while (y < v.yBot) { c.drawRect(v.x + 1f, y, v.x + 3f, y + 2f, paint); y += 8f }
            }
        }
    }

    private fun drawGame(c: Canvas) {
        if (lv.num == 3) {
            for (rx in floatArrayOf(3f, 27f, 43f, 179f, 195f, 219f)) {
                val top = if (rx >= 195f) 48f else 72f
                rect(c, rx, top, rx + 2f, 240f, P_PURP)
            }
        }
        if (lv.num == 1) {
            for (bx in floatArrayOf(88f, 160f, 200f)) {
                var y = 224f
                while (y < 248f) { dst.set(bx, y, bx + 16f, y + 8f); BM["gird1"]?.let { c.drawBitmap(it, null, dst, paint) }; y += 8f }
                dst.set(bx - 4f, 216f, bx + 20f, 224f); BM["grass"]?.let { c.drawBitmap(it, null, dst, paint) }
            }
        }
        for (t in lv.tracks) drawVine(c, t, true)
        for (tw in lv.towers) {
            val b = BM["brick"] ?: continue
            c.save()
            clip.set(tw.x0, tw.yTop, tw.x1, tw.yBot)
            c.clipRect(clip)
            var x = tw.x0
            while (x < tw.x1) {
                var y = tw.yTop
                while (y < tw.yBot) { dst.set(x, y, x + 8f, y + 8f); c.drawBitmap(b, null, dst, paint); y += 8f }
                x += 8f
            }
            c.restore()
            val cap = BM["cap1"]
            if (cap != null) { dst.set(tw.x0, tw.yTop - 8f, tw.x1, tw.yTop); c.drawBitmap(cap, null, dst, paint) }
        }
        for (s in lv.segs) drawRow(c, s)
        for (v in lv.vines) drawVine(c, v, false)
        for (i in lv.elevs.indices) {
            val el = lv.elevs[i]
            paint.color = P_PURP; paint.style = Paint.Style.FILL
            c.drawRect(el.x - 8f, el.yTop - 8f, el.x - 7f, el.yBot, paint)
            c.drawRect(el.x + 7f, el.yTop - 8f, el.x + 8f, el.yBot, paint)
            sprTL(c, BM["elevcap"], el.x - 8f, elevPos[i] - 16f)
        }
        for (l in lv.locks) {
            rect(c, l.cx - 8f, 56f, l.cx + 8f, 72f, P_LOCK)
            rect(c, l.cx - 3f, 62f, l.cx + 3f, 69f, Color.BLACK)
            rect(c, l.cx - 1.5f, 66f, l.cx + 1.5f, 70f, Color.BLACK)
        }
        if (lv.num != 4) {
            val ilx = (lv.insertX0 + lv.insertX1) / 2f
            rect(c, ilx - 9f, lv.insertY - 17f, ilx + 9f, lv.insertY, P_LOCK)
            rect(c, ilx - 4f, lv.insertY - 11f, ilx + 4f, lv.insertY - 4f, Color.BLACK)
        }
        BM["cage"]?.let { cb -> dst.set(lv.cageX, lv.cageY, lv.cageX + cb.width / 8f * lv.cageSc, lv.cageY + cb.height / 8f * lv.cageSc); c.drawBitmap(cb, null, dst, paint) }
        for (i in lv.fruits.indices) {
            if (fruitsGot[i]) continue
            val f = lv.fruits[i]
            val nm = when (f.kind) { 0 -> "fruit_cherry"; 1 -> "fruit_orange"; else -> "fruit_banana" }
            spr(c, nm, f.x, f.y + 10f)
        }
        for (i in lv.keySpots.indices) {
            if (keysTaken[i] != 0) continue
            val k = lv.keySpots[i]
            sprTL(c, BM[lv.keySprite], k.x, k.y)
        }
        if (carrying >= 0) sprTL(c, BM[lv.keySprite], jrX - 7f, jrY - 34f)
        for (e in enemies) {
            when (e.kind) {
                0 -> spr(c, "jaw_red", e.x, e.y + 8f, 1f, e.dir < 0)
                1 -> spr(c, "jaw_blue", e.x, e.y + 10f, 1f, e.dir < 0)
                2 -> spr(c, "bird_nit", e.x, e.y + 6f, 1f, e.b > 0)
                3 -> spr(c, "bird_chain", e.x, e.y + 6f, 1f, e.b > 0)
                4 -> spr(c, "spark_blue", e.x, e.y + 6f, 1f)
            }
        }
        spr(c, "mario", marioX, lv.marioY, 1f, marioDir < 0)
        drawJr(c)
        drawHud(c)
        if (state == 1) {
            text(c, "STAGE " + (loop * 4 + lv.num), 82f, 130f, 10f, P_PINK)
            text(c, "GET READY!", 78f, 145f, 9f, P_WHITE)
        }
        if (state == 4) text(c, "STAGE CLEAR!", 66f, 140f, 11f, P_GOLD)
        if (state == 6) {
            text(c, "GAME OVER", 70f, 120f, 13f, P_RED)
            text(c, "SCORE " + score, 78f, 140f, 9f, P_WHITE)
            if (blink < 0.6f) text(c, "TOUCH TO PLAY", 66f, 165f, 8f, P_CYAN)
        }
    }

    private fun drawJr(c: Canvas) {
        val nm = when {
            jrMode == 1 -> if (((jrAnim * 10f).toInt()) % 2 == 0) "jr_climb0" else "jr_climb1"
            jrMode == 3 -> "jr_idle"
            else -> "jr_run"
        }
        spr(c, nm, jrX, jrY, 1f, jrFace < 0)
    }

    private fun text(c: Canvas, s: String, x: Float, y: Float, size: Float, col: Int) {
        textPaint.color = col; textPaint.textSize = size
        c.drawText(s, x, y, textPaint)
    }

    private fun drawHud(c: Canvas) {
        if (blink < 0.6f) text(c, "1UP", 10f, 14f, 8f, P_RED)
        text(c, String.format("%06d", score), 10f, 24f, 8f, P_WHITE)
        text(c, "HIGH SCORE", 76f, 14f, 7f, P_CYAN)
        text(c, String.format("%06d", hiScore), 84f, 24f, 8f, P_WHITE)
        text(c, "BONUS", 180f, 26f, 7f, P_PINK)
        paint.color = P_WHITE; paint.style = Paint.Style.STROKE; paint.strokeWidth = 1f
        c.drawRect(152f, 28f, 212f, 46f, paint)
        paint.style = Paint.Style.FILL
        text(c, String.format("%04d", bonus), 158f, 42f, 8f, P_CYAN)
        BM[lv.keySprite]?.let { kb -> dst.set(192f, 30f, 192f + kb.width / 8f, 30f + kb.height / 8f); c.drawBitmap(kb, null, dst, paint) }
        text(c, "L=" + String.format("%02d", (lives + 1).coerceAtLeast(1)), 168f, 56f, 7f, P_WHITE)
        var lx = 10f
        for (i in 0 until min(lives, 5)) { spr(c, "jr_idle", lx, 254f, 0.5f); lx += 12f }
    }

    private fun drawTitle(c: Canvas) {
        if (blink < 0.6f) text(c, "1UP", 10f, 14f, 8f, P_RED)
        text(c, String.format("%06d", score), 10f, 24f, 8f, P_WHITE)
        textPaint.color = P_ORANGE_D; textPaint.textSize = 22f
        c.drawText("DONKEY", 30f, 62f, textPaint)
        c.drawText("KONG", 62f, 88f, textPaint)
        textPaint.color = P_RED; textPaint.textSize = 32f
        c.drawText("JR.", 128f, 88f, textPaint)
        sprTL(c, BM["cage"], 148f, 16f, 1.2f)
        spr(c, "jr_idle", 60f, 128f, 1.8f)
        sprTL(c, BM["vine"], 98f, 100f)
        sprTL(c, BM["chain"], 118f, 100f)
        spr(c, "bird_nit", 170f, 142f)
        spr(c, "jaw_red", 40f, 162f)
        if (blink < 0.6f) text(c, "PUSH START", 68f, 180f, 10f, P_WHITE)
        text(c, "HIGH SCORE " + String.format("%06d", hiScore), 54f, 205f, 8f, P_CYAN)
        text(c, "GRAB THE KEYS  FREE YOUR DAD", 22f, 226f, 7f, P_PINK)
        text(c, "LIVES ", 10f, 252f, 8f, P_WHITE)
    }

    private fun drawPad(c: Canvas) {
        paint.style = Paint.Style.FILL
        paint.color = 0xFF3A3A44.toInt()
        dst.set(padRect.left - 6f * scale, padRect.top - 6f * scale, padRect.right + 6f * scale, padRect.bottom + 6f * scale)
        c.drawRoundRect(dst, 12f * scale, 12f * scale, paint)
        paint.color = 0xFF23232B.toInt()
        dst.set(padRect.left, padRect.top, padRect.right, padRect.bottom)
        c.drawRoundRect(dst, 8f * scale, 8f * scale, paint)
        val cx0 = padRect.left + padRect.width() * 0.31f
        val cy0 = padRect.top + padRect.height() * 0.5f
        val ar = padRect.height() * 0.30f
        val up = tU; val dn = tD; val lf = tL; val rt = tR
        fun dz(x0: Float, y0: Float, x1: Float, y1: Float, pressed: Boolean) {
            paint.color = if (pressed) 0xFF9A9AC8.toInt() else 0xFF3C3C48.toInt()
            dst.set(x0, y0, x1, y1)
            c.drawRoundRect(dst, 3f * scale, 3f * scale, paint)
        }
        dz(cx0 - ar * 0.35f, cy0 - ar * 1.45f, cx0 + ar * 0.35f, cy0 - ar * 0.35f, up)
        dz(cx0 - ar * 0.35f, cy0 + ar * 0.35f, cx0 + ar * 0.35f, cy0 + ar * 1.45f, dn)
        dz(cx0 - ar * 1.45f, cy0 - ar * 0.35f, cx0 - ar * 0.35f, cy0 + ar * 0.35f, lf)
        dz(cx0 + ar * 0.35f, cy0 - ar * 0.35f, cx0 + ar * 1.45f, cy0 + ar * 0.35f, rt)
        dz(cx0 - ar * 0.35f, cy0 - ar * 0.35f, cx0 + ar * 0.35f, cy0 + ar * 0.35f, false)
        paint.color = 0xFF555560.toInt()
        dst.set(padRect.left + padRect.width() * 0.36f, padRect.bottom - 12f * scale, padRect.left + padRect.width() * 0.46f, padRect.bottom - 6f * scale)
        c.drawRoundRect(dst, 3f * scale, 3f * scale, paint)
        dst.set(padRect.left + padRect.width() * 0.52f, padRect.bottom - 12f * scale, padRect.left + padRect.width() * 0.62f, padRect.bottom - 6f * scale)
        c.drawRoundRect(dst, 3f * scale, 3f * scale, paint)
        val bxa = padRect.left + padRect.width() * 0.72f
        val bxb = padRect.left + padRect.width() * 0.88f
        val byc = padRect.top + padRect.height() * 0.42f
        paint.color = 0xFF8B1A1A.toInt()
        dst.set(bxa - ar * 0.85f, byc - ar * 0.85f, bxa + ar * 0.85f, byc + ar * 0.85f)
        c.drawOval(dst, paint)
        dst.set(bxb - ar * 0.85f, byc - ar * 0.85f, bxb + ar * 0.85f, byc + ar * 0.85f)
        c.drawOval(dst, paint)
        textPaint.textSize = 10f * scale
        textPaint.color = 0xFFDDDDDD.toInt()
        c.drawText("B", bxa - 3.5f * scale, byc + 3.5f * scale, textPaint)
        c.drawText("A", bxb - 3.5f * scale, byc + 3.5f * scale, textPaint)
    }
}
