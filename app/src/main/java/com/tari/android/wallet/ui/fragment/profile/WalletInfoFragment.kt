package com.tari.android.wallet.ui.fragment.profile

import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.animation.addListener
import androidx.fragment.app.Fragment
import com.daasuu.ei.Ease
import com.daasuu.ei.EasingInterpolator
import com.tari.android.wallet.R
import com.tari.android.wallet.databinding.ActivityWalletInfoBinding
import com.tari.android.wallet.extension.applyFontStyle
import com.tari.android.wallet.ui.activity.settings.SettingsActivity
import com.tari.android.wallet.ui.component.CustomFont
import com.tari.android.wallet.ui.component.EmojiIdCopiedViewController
import com.tari.android.wallet.ui.component.EmojiIdSummaryViewController
import com.tari.android.wallet.ui.extension.*
import com.tari.android.wallet.ui.util.UiUtil
import com.tari.android.wallet.ui.util.UiUtil.updateWidth
import com.tari.android.wallet.util.Constants
import com.tari.android.wallet.util.EmojiUtil
import com.tari.android.wallet.util.SharedPrefsWrapper
import com.tari.android.wallet.util.WalletUtil
import javax.inject.Inject

class WalletInfoFragment : Fragment() {

    @Inject
    lateinit var sharedPrefsWrapper: SharedPrefsWrapper

    @Inject
    lateinit var clipboardManager: ClipboardManager

    private lateinit var ui: ActivityWalletInfoBinding
    private lateinit var dimmerViews: List<View>
    private lateinit var emojiIdSummaryController: EmojiIdSummaryViewController
    private lateinit var emojiIdCopiedViewController: EmojiIdCopiedViewController

    // region Lifecycle
    override fun onAttach(context: Context) {
        super.onAttach(context)
        appComponent.inject(this)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        ui = ActivityWalletInfoBinding.inflate(inflater, container, false)
        dimmerViews =
            listOf(ui.headerDimmerView, ui.scrollDimmerView, ui.qrDimmerView, ui.bottomDimmerView)
        emojiIdSummaryController = EmojiIdSummaryViewController(ui.emojiIdSummaryView)
        val emojiId = sharedPrefsWrapper.emojiId!!
        emojiIdSummaryController.display(emojiId)
        emojiIdCopiedViewController = EmojiIdCopiedViewController(ui.emojiIdCopiedView)
        return ui.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUi()
    }

    // endregion Lifecycle

    // region Initial UI Setup
    private fun setupUi() {
        val emojiId = sharedPrefsWrapper.emojiId!!
        displayStylizedTitle()
        displayQRCode(emojiId)
        displayFullEmojiId(emojiId)
        setupCTAs()
    }

    private fun displayFullEmojiId(emojiId: String) {
        ui.fullEmojiIdTextView.text = EmojiUtil.getFullEmojiIdSpannable(
            emojiId,
            string(R.string.emoji_id_chunk_separator),
            color(R.color.black),
            color(R.color.light_gray)
        )
    }

    private fun displayStylizedTitle() {
        val styledTitle = string(R.string.wallet_info_share_your_emoji_id).applyFontStyle(
            requireContext(),
            CustomFont.AVENIR_LT_STD_LIGHT,
            string(R.string.wallet_info_share_your_emoji_id_bold_part),
            CustomFont.AVENIR_LT_STD_BLACK,
            applyToOnlyFirstOccurence = true
        )
        ui.shareEmojiIdTextView.text = styledTitle
    }

    private fun displayQRCode(emojiId: String) {
        val content = WalletUtil.getEmojiIdDeepLink(emojiId)
        UiUtil.getQREncodedBitmap(content, dimenPx(R.dimen.wallet_info_img_qr_code_size))?.let {
            ui.qrImageView.setImageBitmap(it)
        }
    }

    // endregion Initial UI Setup

    // region CTAs setup

    private fun setupCTAs() {
        ui.emojiIdSummaryContainerView.setOnClickListener(this::onEmojiSummaryClicked)
        ui.copyEmojiIdButton.setOnClickListener(this::onCopyEmojiIdButtonClicked)
        ui.copyEmojiIdButton.setOnLongClickListener { view ->
            onCopyEmojiIdButtonLongClicked(view)
            true
        }
        ui.closeButton.setOnClickListener { this.onCloseButtonClick() }
        ui.openSettingsCtaView.setOnClickListener(ThrottleClick {
            SettingsActivity.launch(requireActivity())
        })
        dimmerViews.forEach { it.setOnClickListener { this.hideFullEmojiId() } }
    }

    private fun onEmojiSummaryClicked(view: View) {
        UiUtil.temporarilyDisableClick(view)
        showFullEmojiId()
    }

    private fun showFullEmojiId() {
        setupDimmersForShowAnimation()
        val fullEmojiIdInitialWidth = ui.emojiIdSummaryContainerView.width
        val fullEmojiIdDeltaWidth = ui.emojiIdContainerView.width - fullEmojiIdInitialWidth
        setupEmojiIdViewsForAnimation(fullEmojiIdInitialWidth)
        val emojiIdAnim = createEmojiIdShowAnimator(fullEmojiIdInitialWidth, fullEmojiIdDeltaWidth)
        val copyEmojiIdButtonAnim = createCopyButtonShowAnimator()
        AnimatorSet().apply {
            playSequentially(emojiIdAnim, copyEmojiIdButtonAnim)
            addListener(onEnd = { dimmerViews.forEach { it.isClickable = true } })
            start()
        }
        ui.fullEmojiIdScrollView.postDelayed(Constants.UI.shortDurationMs + 20) {
            ui.fullEmojiIdScrollView.smoothScrollTo(0, 0)
        }
    }

    private fun setupEmojiIdViewsForAnimation(fullEmojiIdInitialWidth: Int) {
        ui.fullEmojiIdContainerView.updateWidth(fullEmojiIdInitialWidth)
        ui.fullEmojiIdContainerView.alpha = 0F
        ui.fullEmojiIdContainerView.visible()
        // scroll the emoji id horizontal list to end
        ui.fullEmojiIdScrollView.scrollTo(
            ui.fullEmojiIdTextView.width - ui.fullEmojiIdScrollView.width,
            0
        )
        ui.copyEmojiIdContainerView.alpha = 0F
        ui.copyEmojiIdContainerView.visible()
        ui.copyEmojiIdContainerView.translationY = 0F
    }

    private fun setupDimmersForShowAnimation() {
        ui.emojiIdSummaryContainerView.invisible()
        dimmerViews.forEach { dimmerView ->
            // make dimmers non-clickable until the anim is over
            dimmerView.isClickable = false
            dimmerView.alpha = 0F
            dimmerView.visible()
        }
    }

    private fun createEmojiIdShowAnimator(
        fullEmojiIdInitialWidth: Int,
        fullEmojiIdDeltaWidth: Int
    ): ValueAnimator = ValueAnimator.ofFloat(0F, 1F).apply {
        duration = Constants.UI.shortDurationMs
        addUpdateListener { valueAnimator: ValueAnimator ->
            val value = valueAnimator.animatedValue as Float
            dimmerViews.forEach { it.alpha = value * 0.6F }
            ui.fullEmojiIdContainerView.alpha = value
            ui.fullEmojiIdContainerView.scaleX = 1F + 0.2F * (1F - value)
            ui.fullEmojiIdContainerView.scaleY = 1F + 0.2F * (1F - value)
            ui.fullEmojiIdContainerView
                .updateWidth((fullEmojiIdInitialWidth + fullEmojiIdDeltaWidth * value).toInt())
        }
    }

    private fun createCopyButtonShowAnimator(): ValueAnimator =
        ValueAnimator.ofFloat(0F, 1F).apply {
            duration = Constants.UI.shortDurationMs
            interpolator = EasingInterpolator(Ease.BACK_OUT)
            addUpdateListener { valueAnimator: ValueAnimator ->
                val value = valueAnimator.animatedValue as Float
                ui.copyEmojiIdContainerView.alpha = value
                ui.copyEmojiIdContainerView.translationY =
                    -dimenPx(R.dimen.common_copy_emoji_id_button_visible_bottom_margin) * value
            }
        }

    private fun hideFullEmojiId(animateCopyEmojiIdButton: Boolean = true) {
        dimmerViews.forEach(UiUtil::temporarilyDisableClick)
        ui.fullEmojiIdScrollView.smoothScrollTo(0, 0)
        ui.emojiIdSummaryContainerView.visible()
        val fullEmojiIdInitialWidth = ui.emojiIdContainerView.width
        val fullEmojiIdDeltaWidth =
            ui.emojiIdSummaryContainerView.width - ui.emojiIdContainerView.width
        val emojiIdAnim = createEmojiIdHideAnimator(fullEmojiIdInitialWidth, fullEmojiIdDeltaWidth)
        runHideAnimation(animateCopyEmojiIdButton, emojiIdAnim)
    }

    private fun runHideAnimation(
        animateCopyEmojiIdButton: Boolean,
        emojiIdAnim: ValueAnimator
    ) {
        AnimatorSet().apply {
            if (animateCopyEmojiIdButton) {
                playSequentially(createCopyButtonHideAnimator(), emojiIdAnim)
            } else {
                play(emojiIdAnim)
            }
            addListener(onEnd = {
                dimmerViews.forEach(View::gone)
                ui.fullEmojiIdContainerView.gone()
                ui.copyEmojiIdContainerView.gone()
            })
            start()
        }
    }

    private fun createCopyButtonHideAnimator(): ValueAnimator =
        ValueAnimator.ofFloat(1F, 0F).apply {
            addUpdateListener {
                val value = it.animatedValue as Float
                ui.copyEmojiIdContainerView.alpha = value
                ui.copyEmojiIdContainerView.translationY =
                    -dimenPx(R.dimen.common_copy_emoji_id_button_visible_bottom_margin) * value
            }
        }

    private fun createEmojiIdHideAnimator(
        fullEmojiIdInitialWidth: Int,
        fullEmojiIdDeltaWidth: Int
    ): ValueAnimator = ValueAnimator.ofFloat(0F, 1F).apply {
        addUpdateListener { valueAnimator: ValueAnimator ->
            val value = valueAnimator.animatedValue as Float
            // hide overlay dimmers
            dimmerViews.forEach { it.alpha = (1 - value) * 0.6F }
            // container alpha & scale
            ui.fullEmojiIdContainerView.alpha = 1 - value
            ui.fullEmojiIdContainerView
                .updateWidth((fullEmojiIdInitialWidth + fullEmojiIdDeltaWidth * value).toInt())
        }
    }

    private fun completeCopy(clipboardString: String) {
        dimmerViews.forEach { dimmerView -> dimmerView.isClickable = false }
        val clipboardData = ClipData.newPlainText(
            "Tari Wallet Identity",
            clipboardString
        )
        clipboardManager.setPrimaryClip(clipboardData)
        emojiIdCopiedViewController.showEmojiIdCopiedAnim(fadeOutOnEnd = true) {
            hideFullEmojiId(animateCopyEmojiIdButton = false)
        }
        val copyEmojiIdButtonAnim = ui.copyEmojiIdContainerView.animate().alpha(0f)
        copyEmojiIdButtonAnim.duration = Constants.UI.xShortDurationMs
        copyEmojiIdButtonAnim.start()
    }

    private fun onCopyEmojiIdButtonClicked(view: View) {
        UiUtil.temporarilyDisableClick(view)
        completeCopy(sharedPrefsWrapper.emojiId!!)
    }

    private fun onCopyEmojiIdButtonLongClicked(view: View) {
        UiUtil.temporarilyDisableClick(view)
        completeCopy(sharedPrefsWrapper.publicKeyHexString!!)
    }

    private fun onCloseButtonClick() {
        requireActivity().onBackPressed()
    }

    // endregion CTAs setup

}
