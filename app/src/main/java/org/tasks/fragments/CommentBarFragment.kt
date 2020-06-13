package org.tasks.fragments

import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import butterknife.*
import com.todoroo.andlib.utility.AndroidUtilities
import com.todoroo.astrid.data.Task
import org.tasks.R
import org.tasks.Strings.isNullOrEmpty
import org.tasks.activities.CameraActivity
import org.tasks.dialogs.DialogBuilder
import org.tasks.files.ImageHelper
import org.tasks.injection.FragmentComponent
import org.tasks.preferences.Device
import org.tasks.preferences.Preferences
import org.tasks.themes.ThemeColor
import org.tasks.ui.TaskEditControlFragment
import java.util.*
import javax.inject.Inject

class CommentBarFragment : TaskEditControlFragment() {
    @Inject lateinit var activity: Activity
    @Inject lateinit var dialogBuilder: DialogBuilder
    @Inject lateinit var device: Device
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var themeColor: ThemeColor
    
    @BindView(R.id.commentButton)
    lateinit var commentButton: View

    @BindView(R.id.commentField)
    lateinit var commentField: EditText

    @BindView(R.id.picture)
    lateinit var pictureButton: ImageView

    @BindView(R.id.updatesFooter)
    lateinit var commentBar: LinearLayout
    
    private lateinit var callback: CommentBarFragmentCallback
    private var pendingCommentPicture: Uri? = null
    
    override fun onAttach(activity: Activity) {
        super.onAttach(activity)
        callback = activity as CommentBarFragmentCallback
    }

    override fun inject(component: FragmentComponent) = component.inject(this)

    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(layout, container, false)
        ButterKnife.bind(this, view)
        if (savedInstanceState != null) {
            val uri = savedInstanceState.getString(EXTRA_PICTURE)
            if (uri != null) {
                pendingCommentPicture = Uri.parse(uri)
                setPictureButtonToPendingPicture()
            }
            commentField.setText(savedInstanceState.getString(EXTRA_TEXT))
        }
        commentField.setHorizontallyScrolling(false)
        commentField.maxLines = Int.MAX_VALUE
        if (!preferences.getBoolean(R.string.p_show_task_edit_comments, true)) {
            commentBar.visibility = View.GONE
        }
        commentBar.setBackgroundColor(themeColor.primaryColor)
        resetPictureButton()
        return view
    }

    override val layout: Int
        get() = R.layout.fragment_comment_bar

    override val icon: Int
        get() = 0

    override fun controlId() = TAG

    override fun apply(task: Task) {}

    @OnTextChanged(R.id.commentField)
    fun onTextChanged(s: CharSequence) {
        commentButton.visibility = if (pendingCommentPicture == null && isNullOrEmpty(s.toString())) View.GONE else View.VISIBLE
    }

    @OnEditorAction(R.id.commentField)
    fun onEditorAction(key: KeyEvent?): Boolean {
        val actionId = key?.action ?: 0
        if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_NULL) {
            if (commentField.text.isNotEmpty() || pendingCommentPicture != null) {
                addComment()
                return true
            }
        }
        return false
    }

    @OnClick(R.id.commentButton)
    fun addClicked() {
        addComment()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(EXTRA_TEXT, commentField.text.toString())
        if (pendingCommentPicture != null) {
            outState.putString(EXTRA_PICTURE, pendingCommentPicture.toString())
        }
    }

    @OnClick(R.id.picture)
    fun onClickPicture() {
        if (pendingCommentPicture == null) {
            showPictureLauncher(null)
        } else {
            showPictureLauncher {
                pendingCommentPicture = null
                resetPictureButton()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_CODE_CAMERA) {
            if (resultCode == Activity.RESULT_OK) {
                pendingCommentPicture = data!!.data
                setPictureButtonToPendingPicture()
                commentField.requestFocus()
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private fun addComment() {
        addComment(commentField.text.toString())
        AndroidUtilities.hideSoftInputForViews(activity, commentField)
    }

    private fun setPictureButtonToPendingPicture() {
        val bitmap = ImageHelper.sampleBitmap(
                activity,
                pendingCommentPicture,
                pictureButton.layoutParams.width,
                pictureButton.layoutParams.height)
        pictureButton.setImageBitmap(bitmap)
        commentButton.visibility = View.VISIBLE
    }

    private fun addComment(message: String) {
        val picture = pendingCommentPicture
        commentField.setText("")
        pendingCommentPicture = null
        resetPictureButton()
        callback.addComment(if (isNullOrEmpty(message)) " " else message, picture)
    }

    private fun resetPictureButton() {
        val typedValue = TypedValue()
        activity.theme.resolveAttribute(R.attr.colorOnPrimary, typedValue, true)
        val drawable = activity.getDrawable(R.drawable.ic_outline_photo_camera_24px)!!.mutate()
        drawable.setTint(typedValue.data)
        pictureButton.setImageDrawable(drawable)
    }

    private fun showPictureLauncher(clearImageOption: (() -> Unit)?) {
        val runnables: MutableList<() -> Unit> = ArrayList()
        val options: MutableList<String> = ArrayList()
        val cameraAvailable = device.hasCamera()
        if (cameraAvailable) {
            runnables.add {
                startActivityForResult(
                        Intent(activity, CameraActivity::class.java), REQUEST_CODE_CAMERA)
            }
            options.add(getString(R.string.take_a_picture))
        }
        if (clearImageOption != null) {
            runnables.add { clearImageOption.invoke() }
            options.add(getString(R.string.actfm_picture_clear))
        }
        if (runnables.size == 1) {
            runnables[0].invoke()
        } else {
            val listener = DialogInterface.OnClickListener { d: DialogInterface, which: Int ->
                runnables[which].invoke()
                d.dismiss()
            }

            // show a menu of available options
            dialogBuilder.newDialog().setItems(options, listener).show().setOwnerActivity(activity)
        }
    }

    interface CommentBarFragmentCallback {
        fun addComment(message: String?, picture: Uri?)
    }

    companion object {
        const val TAG = R.string.TEA_ctrl_comments
        private const val REQUEST_CODE_CAMERA = 60
        private const val EXTRA_TEXT = "extra_text"
        private const val EXTRA_PICTURE = "extra_picture"
    }
}