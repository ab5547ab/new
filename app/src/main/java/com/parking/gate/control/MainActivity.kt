package com.parking.gate.control

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton

/**
 * מסך ראשי - מציג כפתורים לפתיחת שערי חנייה.
 * לחיצה על כפתור מבצעת חיוג אוטומטי למספר המשויך לשער.
 * אם הטלפון מחובר בבלוטוס למולטימדיה של הרכב, מערכת האנדרואיד
 * מנתבת את השיחה לרכב אוטומטית - ללא צורך בקוד נוסף.
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val PERMISSION_CALL_PHONE = 1001
        private const val PREFS_NAME = "ParkingGatePrefs"
        private const val GATES_KEY = "gates_v1"
    }

    data class Gate(var id: String, var name: String, var phone: String)

    private lateinit var prefs: SharedPreferences
    private lateinit var gatesContainer: GridLayout
    private var gates = mutableListOf<Gate>()
    private var mediaPlayer: MediaPlayer? = null
    private var pendingCallGate: Gate? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        gatesContainer = findViewById(R.id.gatesContainer)

        loadGates()
        renderGates()

        findViewById<ImageButton>(R.id.settingsButton).setOnClickListener {
            showSettingsMenu()
        }

        findViewById<MaterialButton>(R.id.addGateButton).setOnClickListener {
            showAddGateDialog()
        }

        askCallPermissionIfNeeded()
    }

    // ---------------------------------------------------------------
    // הרשאות
    // ---------------------------------------------------------------
    private fun askCallPermissionIfNeeded() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CALL_PHONE),
                PERMISSION_CALL_PHONE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_CALL_PHONE &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            pendingCallGate?.let { dialGate(it) }
        }
        pendingCallGate = null
    }

    // ---------------------------------------------------------------
    // ניהול נתוני שערים (SharedPreferences, פורמט טקסט פשוט)
    // ---------------------------------------------------------------
    private fun loadGates() {
        gates.clear()
        val raw = prefs.getString(GATES_KEY, null)
        if (!raw.isNullOrEmpty()) {
            raw.split("§§").forEach { entry ->
                val parts = entry.split("::")
                if (parts.size == 3) {
                    gates.add(Gate(parts[0], parts[1], parts[2]))
                }
            }
        }
        if (gates.isEmpty()) {
            gates.add(Gate("gate_upper", "חנייה עילית", "0559643981"))
            gates.add(Gate("gate_underground", "חנייה תת קרקעית", "0559643987"))
            persistGates()
        }
    }

    private fun persistGates() {
        val raw = gates.joinToString("§§") { "${it.id}::${it.name}::${it.phone}" }
        prefs.edit().putString(GATES_KEY, raw).apply()
    }

    // ---------------------------------------------------------------
    // בניית הממשק - דינאמי לפי מספר השערים וגודל המסך
    // ---------------------------------------------------------------
    private fun renderGates() {
        gatesContainer.removeAllViews()
        gatesContainer.columnCount = 2

        val screenWidthDp = resources.configuration.screenWidthDp
        if (screenWidthDp < 360) {
            gatesContainer.columnCount = 1
        }

        gates.forEachIndexed { index, gate ->
            val card = buildGateCard(gate)
            val params = GridLayout.LayoutParams().apply {
                width = 0
                height = GridLayout.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(
                    index % gatesContainer.columnCount,
                    1f
                )
                rowSpec = GridLayout.spec(index / gatesContainer.columnCount)
                setMargins(dp(8), dp(8), dp(8), dp(8))
            }
            gatesContainer.addView(card, params)
        }
    }

    private fun buildGateCard(gate: Gate): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = ContextCompat.getDrawable(
                this@MainActivity, R.drawable.gate_button_background
            )
            elevation = dp(3).toFloat()
            setPadding(dp(16), dp(24), dp(16), dp(20))
            isClickable = true
            isFocusable = true

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(170)
            )

            setOnClickListener { onGateTapped(gate) }
            setOnLongClickListener { showEditGateDialog(gate); true }
        }

        val icon = TextView(this).apply {
            text = "🅿️"
            textSize = 44f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val name = TextView(this).apply {
            text = gate.name
            textSize = 17f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_primary))
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, 0)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        container.addView(icon)
        container.addView(name)
        return container
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    // ---------------------------------------------------------------
    // חיוג
    // ---------------------------------------------------------------
    private fun onGateTapped(gate: Gate) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
            == PackageManager.PERMISSION_GRANTED
        ) {
            dialGate(gate)
        } else {
            pendingCallGate = gate
            askCallPermissionIfNeeded()
        }
    }

    private fun dialGate(gate: Gate) {
        try {
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:" + gate.phone)
            }
            startActivity(intent)
            Toast.makeText(
                this,
                getString(R.string.calling_to, gate.name),
                Toast.LENGTH_SHORT
            ).show()
            playGateOpenSound()
        } catch (e: Exception) {
            Toast.makeText(this, "שגיאה בחיוג: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun playGateOpenSound() {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer.create(this, R.raw.gate_open)
            mediaPlayer?.setOnCompletionListener { it.release() }
            mediaPlayer?.start()
        } catch (e: Exception) {
            // אם הצליל נכשל מכל סיבה, לא מפילים את האפליקציה
        }
    }

    // ---------------------------------------------------------------
    // הגדרות - עריכה / הוספה / מחיקה
    // ---------------------------------------------------------------
    private fun showSettingsMenu() {
        if (gates.isEmpty()) {
            showAddGateDialog()
            return
        }
        val names = gates.map { it.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.settings))
            .setItems(names) { _, which -> showEditGateDialog(gates[which]) }
            .setPositiveButton(getString(R.string.add_gate)) { _, _ -> showAddGateDialog() }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun buildFormView(existing: Gate?): Triple<LinearLayout, EditText, EditText> {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(20)
            setPadding(pad, pad, pad, pad)
        }

        val nameInput = EditText(this).apply {
            hint = getString(R.string.gate_name_hint)
            setText(existing?.name ?: "")
        }

        val phoneInput = EditText(this).apply {
            hint = getString(R.string.phone_hint)
            inputType = InputType.TYPE_CLASS_PHONE
            setText(existing?.phone ?: "")
        }

        layout.addView(nameInput)
        layout.addView(phoneInput)
        return Triple(layout, nameInput, phoneInput)
    }

    private fun showAddGateDialog() {
        val (view, nameInput, phoneInput) = buildFormView(null)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.add_gate))
            .setView(view)
            .setPositiveButton(getString(R.string.add)) { _, _ ->
                val name = nameInput.text.toString().trim()
                val phone = phoneInput.text.toString().trim()
                if (name.isEmpty() || phone.isEmpty()) {
                    Toast.makeText(this, getString(R.string.fill_all_fields), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                gates.add(Gate("gate_" + System.currentTimeMillis(), name, phone))
                persistGates()
                renderGates()
                Toast.makeText(this, getString(R.string.gate_added), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showEditGateDialog(gate: Gate) {
        val (view, nameInput, phoneInput) = buildFormView(gate)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.edit_gate))
            .setView(view)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val name = nameInput.text.toString().trim()
                val phone = phoneInput.text.toString().trim()
                if (name.isEmpty() || phone.isEmpty()) {
                    Toast.makeText(this, getString(R.string.fill_all_fields), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                gate.name = name
                gate.phone = phone
                persistGates()
                renderGates()
            }
            .setNeutralButton(getString(R.string.delete)) { _, _ ->
                gates.remove(gate)
                persistGates()
                renderGates()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}
