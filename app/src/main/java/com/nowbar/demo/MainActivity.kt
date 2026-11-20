package com.eren.kocluk

import android.app.Activity
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class Blk(val t: String, val s: String, val d: String)
class Day(val a: String, val n: String, val o: String, val b: List<Blk>)

private const val M = "Okulda işlenen konuyu defterden tekrar et. Kuralı kâğıda yaz, 5 örnek soru çöz. Takıldığın soruyu hata defterine ekle."
private const val K = "Son işlenen üniteyi defterden özetle. Kitabı kapat, hatırladıklarını yaz, sonra kontrol et."
private const val T = "Son işlenen konudan 1 açık uçlu soru yaz ve 5-6 cümleyle cevapla."
private const val B = "Konuyu şemaya dök. Kitabı kapat, şemayı baştan çiz, eksiklerini tamamla."
private const val F = "Formülleri ve tanımları yaz. 3 soru çöz, yanlışları hata defterine ekle."
private val HATA = Blk("20:40-21:00", "Hata defteri", "Bugünkü yanlışları ve nedenlerini yaz. Yarının çantasını hazırla.")

private val DAYS = listOf(
    Day("Pzt", "Pazartesi", "Okul: Matematik, Siyer, Kuran", listOf(
        Blk("19:00-19:45", "Matematik", M), Blk("19:55-20:40", "Kuran-ı Kerim", K), HATA)),
    Day("Sal", "Salı", "Okul: Kimya, İngilizce, Matematik, TDE", listOf(
        Blk("19:00-19:45", "Matematik", M), Blk("19:55-20:40", "Türk Dili ve Edebiyatı", T), HATA)),
    Day("Çar", "Çarşamba", "Okul: TDE, Coğrafya, Arapça, Hadis", listOf(
        Blk("19:00-19:45", "Türk Dili ve Edebiyatı", T), Blk("19:55-20:40", "Kuran-ı Kerim", K), HATA)),
    Day("Per", "Perşembe", "Okul: Matematik, TDE, Biyoloji, Fıkıh", listOf(
        Blk("19:00-19:45", "Matematik", M), Blk("19:55-20:40", "Biyoloji", B), HATA)),
    Day("Cum", "Cuma", "Okul: Fizik, Tarih, Felsefe, Kuran", listOf(
        Blk("19:00-19:45", "Türk Dili ve Edebiyatı", "Haftanın açık uçlu sorusunu yaz ve koçuna gönder."),
        Blk("19:55-20:40", "Fizik", F), HATA)),
    Day("Cmt", "Cumartesi", "Toplam 2 saat, sabah veya öğleden sonra", listOf(
        Blk("60 dk", "Matematik", "Haftanın konularından soru çöz. En zayıf konuya 30 dk ayır."),
        Blk("30 dk", "Hata defteri tekrarı", "Haftanın yanlışlarını kapatıp tekrar çöz."),
        Blk("30 dk", "TDE", "1 açık uçlu soruyu zamana karşı yaz."))),
    Day("Paz", "Pazar", "Toplam 1 saat, gerisi dinlenme", listOf(
        Blk("45 dk", "Haftalık tekrar", "Bu haftanın 3 konusunu kitapsız anlat veya yaz."),
        Blk("15 dk", "Haftanın planı", "Logu Claude'a gönder. Yazılı tarihlerini güncelle.")))
)

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var perm: Button
    private lateinit var tabs: LinearLayout
    private lateinit var dayTitle: TextView
    private lateinit var listBox: LinearLayout
    private lateinit var note: EditText
    private lateinit var out: TextView
    private var gi = 0
    private var sel = 0
    private var weekKey = ""
    private val prefs by lazy { getSharedPreferences("kocluk", Context.MODE_PRIVATE) }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        val cal = Calendar.getInstance()
        gi = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7
        sel = gi
        cal.add(Calendar.DAY_OF_YEAR, -gi)
        weekKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(30, 110, 30, 40)
        }
        status = TextView(this).apply { textSize = 14f }
        perm = Button(this).apply {
            text = "Kullanım iznini ver"
            setOnClickListener { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
        }
        tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val hs = HorizontalScrollView(this).apply { addView(tabs) }
        dayTitle = TextView(this).apply { textSize = 18f; setPadding(0, 24, 0, 8) }
        listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        note = EditText(this).apply { hint = "Bugünkü not (ders, dk, soru sayısı)" }
        out = TextView(this).apply { textSize = 13f; setPadding(0, 30, 0, 0) }

        fun btn(t: String, f: () -> Unit) = Button(this).apply {
            text = t
            setOnClickListener { f() }
        }
        box.addView(status)
        box.addView(perm)
        box.addView(hs)
        box.addView(dayTitle)
        box.addView(listBox)
        box.addView(note)
        box.addView(btn("Bugünü kaydet") { saveToday() })
        box.addView(btn("Logu paylaş (Claude'a gönder)") { share() })
        box.addView(btn("Logu kopyala") { copyLog() })
        box.addView(btn("Logu sil") { logFile().delete(); showLog() })
        box.addView(out)
        setContentView(ScrollView(this).apply { addView(box) })
        render()
    }

    override fun onResume() {
        super.onResume()
        val ok = hasAccess()
        status.text = if (ok) "Kullanım izni: VAR" else "Kullanım izni: YOK. Önce izni ver."
        perm.visibility = if (ok) View.GONE else View.VISIBLE
        showLog()
    }

    private fun render() {
        tabs.removeAllViews()
        DAYS.forEachIndexed { i, d ->
            tabs.addView(Button(this).apply {
                text = if (i == gi) d.a + "•" else d.a
                minWidth = 0
                minimumWidth = 0
                setPadding(26, 0, 26, 0)
                alpha = if (i == sel) 1f else 0.5f
                setOnClickListener { sel = i; render() }
            })
        }
        val d = DAYS[sel]
        dayTitle.text = d.n + (if (sel == gi) " (bugün)" else "") + "\n" + d.o
        listBox.removeAllViews()
        d.b.forEachIndexed { j, b ->
            val key = "${weekKey}_${sel}_$j"
            listBox.addView(CheckBox(this).apply {
                text = "${b.t}  ${b.s}\n${b.d}"
                textSize = 15f
                setPadding(8, 16, 8, 16)
                isChecked = prefs.getBoolean(key, false)
                setOnCheckedChangeListener { _, c -> prefs.edit().putBoolean(key, c).apply() }
            })
        }
    }

    private fun logFile() = File(filesDir, "log.txt")

    private fun hasAccess(): Boolean {
        val ops = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        return ops.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName
        ) == AppOpsManager.MODE_ALLOWED
    }

    private fun fmt(ms: Long): String {
        val m = ms / 60000
        return if (m >= 60) "${m / 60} sa ${m % 60} dk" else "$m dk"
    }

    private fun label(p: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(p, 0)).toString()
    } catch (e: Exception) { p }

    private fun saveToday() {
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        val sb = StringBuilder("=== $date ===\n")
        if (hasAccess()) {
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val rows = usm.queryAndAggregateUsageStats(cal.timeInMillis, System.currentTimeMillis())
                .values.filter { it.totalTimeInForeground > 60_000 }
                .sortedByDescending { it.totalTimeInForeground }
            sb.append("Toplam ekran: ${fmt(rows.sumOf { it.totalTimeInForeground })}\n")
            rows.take(15).forEach {
                sb.append("${label(it.packageName)}: ${fmt(it.totalTimeInForeground)}\n")
            }
        } else {
            sb.append("Kullanım izni yok, ekran süresi yazılamadı.\n")
        }
        val today = DAYS[gi]
        val done = ArrayList<String>()
        val todo = ArrayList<String>()
        today.b.forEachIndexed { j, b ->
            val ok = prefs.getBoolean("${weekKey}_${gi}_$j", false)
            (if (ok) done else todo).add("${b.s} (${b.t})")
        }
        sb.append("${today.n} yapılan: ${if (done.isEmpty()) "yok" else done.joinToString(", ")}\n")
        sb.append("${today.n} yapılmayan: ${if (todo.isEmpty()) "yok" else todo.joinToString(", ")}\n")
        if (note.text.isNotBlank()) sb.append("Not: ${note.text}\n")
        sb.append("\n")
        logFile().appendText(sb.toString())
        note.setText("")
        showLog()
        Toast.makeText(this, "Kaydedildi", Toast.LENGTH_SHORT).show()
    }

    private fun logText() = if (logFile().exists()) logFile().readText() else ""

    private fun showLog() {
        out.text = logText().ifEmpty { "Henüz kayıt yok." }
    }

    private fun share() {
        val t = logText()
        if (t.isEmpty()) return
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, t)
        }
        startActivity(Intent.createChooser(i, "Logu gönder"))
    }

    private fun copyLog() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("log", logText()))
        Toast.makeText(this, "Kopyalandı", Toast.LENGTH_SHORT).show()
    }
}
