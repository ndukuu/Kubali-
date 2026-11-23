package com.example.studentsupport

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : ComponentActivity() {

    private var currentUser: AppUser? = null
    private var pendingRequestId = -1
    private var selectedFinanceId = -1

    // ---------- File pickers (Activity Result API) ----------

    private val abstractPicker =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            val u = currentUser
            if (uri != null && u != null) {
                report(
                    RequestRepository.submitAbstract(pendingRequestId, u.admissionNo, uri.toString()),
                    "Abstract submitted for review"
                )
                showFinance()
            }
        }

    private val receiptPicker =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            val u = currentUser
            if (uri != null && u != null) {
                report(
                    RequestRepository.submitReceipt(pendingRequestId, u.admissionNo, uri.toString()),
                    "Receipt submitted for verification"
                )
                showFinance()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showLogin()
    }

    // =====================================================
    // Registration and login
    // =====================================================

    private fun showLogin() {
        screen("Student Support System") { c ->
            label(c, "Sign in to continue")
            val role = roleSpinner()
            add(c, role)
            val email = input(c, "Email", InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
            val pass = input(c, "Password", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
            button(c, "Log in", {
                val user = RequestRepository.login(
                    email.text.toString().trim(), pass.text.toString(), selectedRole(role)
                )
                if (user == null) {
                    toast("Invalid email, password or account type")
                } else {
                    currentUser = user
                    if (user.role == Role.ADMIN) showAdminHome() else showStudentHome()
                }
            })
            linkButton(c, "Create an account", { showSignup() })
        }
    }

    private fun showSignup() {
        screen("Create account") { c ->
            val role = roleSpinner()
            add(c, role)
            val name = input(c, "Full name")
            val adm = input(c, "Admission number (students only)")
            val email = input(c, "Email", InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
            val pass = input(c, "Password", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
            button(c, "Sign up", {
                val err = RequestRepository.register(
                    name.text.toString(), adm.text.toString(), email.text.toString(),
                    pass.text.toString(), selectedRole(role)
                )
                if (err == null) {
                    toast("Account created. Please log in.")
                    showLogin()
                } else toast(err)
            })
            linkButton(c, "Back to login", { showLogin() })
        }
    }

    private fun roleSpinner(): Spinner = Spinner(this).apply {
        adapter = ArrayAdapter(
            this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
            listOf("Student", "Administrator")
        )
    }

    private fun selectedRole(s: Spinner) = if (s.selectedItemPosition == 0) Role.STUDENT else Role.ADMIN

    // =====================================================
    // Student dashboard and temporary pass
    // =====================================================

    private fun showStudentHome() {
        val u = currentUser ?: return showLogin()
        screen("Welcome, ${u.fullName}") { c ->
            label(c, "Admission no: ${u.admissionNo}")
            button(c, "Request a temporary pass", { showPassForm() })
            button(c, "Report a lost student ID", { showLostForm() })
            button(c, "My temporary pass requests", { showStudentStatuses(true) })
            button(c, "My lost ID requests", { showStudentStatuses(false) })
            button(c, "Finance Portal", { showFinance() })
            linkButton(c, "Log out", { currentUser = null; showLogin() })
        }
    }

    private fun showPassForm() {
        val u = currentUser ?: return showLogin()
        screen("Temporary pass") { c ->
            label(c, "Name: ${u.fullName}")
            label(c, "Admission no: ${u.admissionNo}")
            val date = input(c, "Date needed (YYYY-MM-DD)")
            button(c, "Submit request", {
                val err = RequestRepository.submitPass(u.admissionNo, u.fullName, date.text.toString())
                if (err == null) {
                    toast("Temporary pass requested")
                    showStudentHome()
                } else toast(err)
            })
            linkButton(c, "Back", { showStudentHome() })
        }
    }

    // =====================================================
    // Lost ID request and status tracking
    // =====================================================

    private fun showLostForm() {
        val u = currentUser ?: return showLogin()
        screen("Lost student ID") { c ->
            label(c, "Name: ${u.fullName}")
            label(c, "Admission no: ${u.admissionNo}")
            val date = input(c, "Date lost (YYYY-MM-DD)")
            button(c, "Submit request", {
                val err = RequestRepository.createLostId(u.admissionNo, u.fullName, date.text.toString())
                if (err == null) {
                    val id = RequestRepository.lostFor(u.admissionNo).first().id
                    toast("Request #$id created. Continue in the Finance Portal.")
                    showStudentHome()
                } else toast(err)
            })
            linkButton(c, "Back", { showStudentHome() })
        }
    }

    private fun showStudentStatuses(isPass: Boolean) {
        val u = currentUser ?: return showLogin()
        screen(if (isPass) "My temporary passes" else "My lost ID requests") { c ->
            if (isPass) {
                val list = RequestRepository.passesFor(u.admissionNo)
                if (list.isEmpty()) label(c, "No requests yet")
                list.forEach { r ->
                    val card = card(c)
                    label(card, "Pass #${r.id} for ${r.date}", bold = true)
                    label(card, "Status: ${r.status.name.lowercase().replaceFirstChar { it.uppercase() }}")
                    if (r.reason.isNotBlank()) label(card, "Reason: ${r.reason}")
                }
            } else {
                val list = RequestRepository.lostFor(u.admissionNo)
                if (list.isEmpty()) label(c, "No requests yet")
                list.forEach { r ->
                    val card = card(c)
                    label(card, "Lost ID #${r.id} (lost ${r.dateLost})", bold = true)
                    label(card, "Status: ${r.status.label()}")
                    label(card, lostStatusDescription(r))
                }
            }
            linkButton(c, "Back", { showStudentHome() })
        }
    }

    private fun lostStatusDescription(r: LostRequest): String =
        when (r.status) {
            LostStatus.AWAITING_ABSTRACT -> "Next step: upload your police abstract in the Finance Portal."
            LostStatus.ABSTRACT_SUBMITTED -> "Your abstract is waiting for admin review."
            LostStatus.ABSTRACT_REJECTED -> "Your abstract was rejected: ${r.reason}. Upload a corrected abstract."
            LostStatus.ABSTRACT_APPROVED -> "Abstract approved. Record your payment reference in the Finance Portal."
            LostStatus.AWAITING_RECEIPT -> "Payment reference recorded. Upload your payment receipt."
            LostStatus.RECEIPT_SUBMITTED -> "Your receipt is waiting for verification."
            LostStatus.PROCESSING -> "Receipt verified. Your replacement ID is being processed."
            LostStatus.READY -> "Your replacement ID is ready for collection."
        }

    // =====================================================
    // Finance Portal and document uploads
    // =====================================================

    private fun showFinance() {
        val u = currentUser ?: return showLogin()
        screen("Finance Portal") { c ->
            val list = RequestRepository.lostFor(u.admissionNo)
            if (list.isEmpty()) {
                label(c, "You have no lost ID requests yet.")
            } else {
                label(c, "Select a lost ID request:")
                list.forEach { r ->
                    linkButton(c, "Request #${r.id} - ${r.status.label()}", {
                        selectedFinanceId = r.id
                        showFinance()
                    })
                }
                val selected = list.firstOrNull { it.id == selectedFinanceId }
                if (selected != null) renderFinanceDetails(c, selected, u)
            }
            linkButton(c, "Back", { showStudentHome() })
        }
    }

    private fun renderFinanceDetails(root: LinearLayout, r: LostRequest, u: AppUser) {
        val card = card(root)
        label(card, "Request #${r.id}", bold = true)
        label(card, "Status: ${r.status.label()}")
        label(card, lostStatusDescription(r))
        r.paymentRef?.let { label(card, "Payment reference: $it") }

        when (r.status) {
            LostStatus.AWAITING_ABSTRACT, LostStatus.ABSTRACT_REJECTED ->
                button(card, "Upload police abstract (PDF)", {
                    pendingRequestId = r.id
                    abstractPicker.launch("application/pdf")
                })

            LostStatus.ABSTRACT_APPROVED -> {
                val input = input(card, "Payment reference")
                button(card, "Record payment reference", {
                    report(
                        RequestRepository.makePayment(r.id, u.admissionNo, input.text.toString()),
                        "Payment reference recorded"
                    )
                    showFinance()
                })
            }

            LostStatus.AWAITING_RECEIPT ->
                button(card, "Upload payment receipt (PDF)", {
                    pendingRequestId = r.id
                    receiptPicker.launch("application/pdf")
                })

            else -> {}
        }
    }

    // =====================================================
    // Admin dashboard: temporary passes
    // =====================================================

    private fun showAdminHome() {
        screen("Admin dashboard") { c ->
            label(c, "Temporary pass requests", bold = true)
            val passes = RequestRepository.passes.sortedByDescending { it.id }
            if (passes.isEmpty()) label(c, "No pass requests")
            passes.forEach { r ->
                val card = card(c)
                label(card, "Pass #${r.id} - ${r.fullName} (${r.admissionNo})", bold = true)
                label(card, "Date: ${r.date}")
                label(card, "Status: ${r.status.name.lowercase().replaceFirstChar { it.uppercase() }}")
                if (r.reason.isNotBlank()) label(card, "Reason: ${r.reason}")
                if (r.status == PassStatus.PENDING) {
                    button(card, "Approve", {
                        RequestRepository.approvePass(r.id)
                        showAdminHome()
                    })
                    button(card, "Reject", {
                        rejectDialog("Reject pass #${r.id}") { reason ->
                            RequestRepository.rejectPass(r.id, reason)
                            showAdminHome()
                        }
                    })
                }
            }

            // =================================================
            // Admin dashboard: lost ID requests
            // =================================================

            label(c, "Lost ID requests", bold = true)
            val lost = RequestRepository.lostIds.sortedByDescending { it.id }
            if (lost.isEmpty()) label(c, "No lost ID requests")
            lost.forEach { r ->
                val card = card(c)
                label(card, "Lost ID #${r.id} - ${r.fullName} (${r.admissionNo})", bold = true)
                label(card, "Date lost: ${r.dateLost}")
                label(card, "Status: ${r.status.label()}")
                r.abstractUri?.let { label(card, "Abstract: $it") }
                r.paymentRef?.let { label(card, "Payment reference: $it") }
                r.receiptUri?.let { label(card, "Receipt: $it") }
                if (r.reason.isNotBlank()) label(card, "Reason: ${r.reason}")

                when (r.status) {
                    LostStatus.ABSTRACT_SUBMITTED -> {
                        button(card, "Approve abstract", {
                            RequestRepository.approveAbstract(r.id)
                            showAdminHome()
                        })
                        button(card, "Reject abstract", {
                            rejectDialog("Reject abstract #${r.id}") { reason ->
                                RequestRepository.rejectAbstract(r.id, reason)
                                showAdminHome()
                            }
                        })
                    }
                    LostStatus.RECEIPT_SUBMITTED ->
                        button(card, "Verify receipt", {
                            RequestRepository.verifyReceipt(r.id)
                            showAdminHome()
                        })
                    LostStatus.PROCESSING ->
                        button(card, "Mark ID ready for collection", {
                            RequestRepository.markReady(r.id)
                            showAdminHome()
                        })
                    else -> {}
                }
            }
            linkButton(c, "Log out", { currentUser = null; showLogin() })
        }
    }

    /** Reusable dialog that collects a rejection reason. */
    private fun rejectDialog(title: String, onReason: (String) -> Unit) {
        val box = EditText(this).apply { hint = "Reason for rejection" }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(box)
            .setPositiveButton("Reject") { _, _ ->
                val reason = box.text.toString().trim()
                onReason(if (reason.isEmpty()) "No reason given" else reason)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // =====================================================
    // UI helpers
    // =====================================================

    private val primary = Color.parseColor("#1F4E79")
    private val background = Color.parseColor("#F3F6FA")

    private fun screen(title: String, body: (LinearLayout) -> Unit) {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(32), dp(16), dp(24))
        }
        val scroll = ScrollView(this).apply {
            setBackgroundColor(background)
            addView(root)
        }
        root.addView(TextView(this).apply {
            text = title
            textSize = 24f
            setTypeface(null, Typeface.BOLD)
            setTextColor(primary)
        })
        body(root)
        setContentView(scroll)
    }

    private fun add(parent: LinearLayout, v: View) {
        parent.addView(v, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(10) })
    }

    private fun card(parent: LinearLayout): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), Color.parseColor("#D5DCE6"))
            }
        }
        add(parent, card)
        return card
    }

    private fun label(parent: LinearLayout, text: String, bold: Boolean = false) {
        add(parent, TextView(this).apply {
            this.text = text
            textSize = 15f
            setTextColor(Color.parseColor("#222222"))
            if (bold) setTypeface(null, Typeface.BOLD)
        })
    }

    private fun input(parent: LinearLayout, hint: String, type: Int = InputType.TYPE_CLASS_TEXT): EditText {
        val e = EditText(this).apply {
            this.hint = hint
            inputType = type
            setBackgroundColor(Color.WHITE)
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        add(parent, e)
        return e
    }

    private fun button(parent: LinearLayout, text: String, onClick: () -> Unit): Button {
        val b = Button(this).apply {
            this.text = text
            setTextColor(Color.WHITE)
            isAllCaps = false
            background = GradientDrawable().apply {
                setColor(primary)
                cornerRadius = dp(10).toFloat()
            }
            setOnClickListener { onClick() }
        }
        add(parent, b)
        return b
    }

    private fun linkButton(parent: LinearLayout, text: String, onClick: () -> Unit) {
        add(parent, Button(this).apply {
            this.text = text
            isAllCaps = false
            setTextColor(primary)
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener { onClick() }
        })
    }

    private fun LostStatus.label(): String = when (this) {
        LostStatus.AWAITING_ABSTRACT -> "Awaiting police abstract"
        LostStatus.ABSTRACT_SUBMITTED -> "Abstract under review"
        LostStatus.ABSTRACT_REJECTED -> "Abstract rejected"
        LostStatus.ABSTRACT_APPROVED -> "Abstract approved"
        LostStatus.AWAITING_RECEIPT -> "Awaiting receipt"
        LostStatus.RECEIPT_SUBMITTED -> "Receipt under review"
        LostStatus.PROCESSING -> "ID being processed"
        LostStatus.READY -> "Ready for collection"
    }

    private fun report(error: String?, success: String) = toast(error ?: success)

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
