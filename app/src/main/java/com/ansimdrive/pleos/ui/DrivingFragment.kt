package com.ansimdrive.pleos.ui

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.ansimdrive.pleos.R
import com.ansimdrive.pleos.databinding.FragmentDrivingBinding

/**
 * 주행 중: 검정 배경에 "운전 중" 한 줄과 작은 헤맴 감지 상태 아이콘만.
 * 질문할 때는 위 절반에 문구, 아래 절반에 큰 '네 / 아니요' 버튼을 보인다.
 * 안내 음성을 낼 수 없을 때는 같은 문장을 화면 아래에 자막으로 잠깐 보인다.
 */
class DrivingFragment : Fragment() {

    private var _binding: FragmentDrivingBinding? = null
    private val binding get() = _binding!!
    private val hideCaption = Runnable { _binding?.caption?.isVisible = false }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDrivingBinding.inflate(inflater, container, false)
        return binding.root
    }

    /** 헤맴 감지가 지금 동작 중인지(위치 수신 중이고 억제 조건이 아님) 아이콘 색으로 보인다. */
    fun setDetectionActive(active: Boolean) {
        val b = _binding ?: return
        val color = ContextCompat.getColor(requireContext(), if (active) R.color.detection_on else R.color.detection_off)
        b.detectionIcon.imageTintList = ColorStateList.valueOf(color)
        b.detectionIcon.contentDescription = getString(if (active) R.string.detection_on else R.string.detection_off)
    }

    /** 질문과 함께 버튼을 바로 보인다. 음성 대답을 듣는 동안에도 누를 수 있다. */
    fun showQuestion(text: String, onYes: () -> Unit, onNo: () -> Unit) {
        val b = _binding ?: return
        b.question.text = text
        b.yes.setOnClickListener { onYes() }
        b.no.setOnClickListener { onNo() }
        b.status.isVisible = false
        b.detectionIcon.isVisible = false
        b.caption.isVisible = false
        b.questionPanel.isVisible = true
    }

    /** 소리 대신 보이는 자막. [CAPTION_MS] 뒤 사라진다. */
    fun showCaption(text: String) {
        val b = _binding ?: return
        b.caption.text = text
        b.caption.isVisible = true
        b.root.removeCallbacks(hideCaption)
        b.root.postDelayed(hideCaption, CAPTION_MS)
    }

    fun clearQuestion() {
        val b = _binding ?: return
        b.questionPanel.isVisible = false
        b.status.isVisible = true
        b.detectionIcon.isVisible = true
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.root.removeCallbacks(hideCaption)
        _binding = null
    }

    private companion object {
        const val CAPTION_MS = 6_000L
    }
}
