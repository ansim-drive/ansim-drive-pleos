package com.ansimdrive.pleos.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.ansimdrive.pleos.databinding.FragmentDrivingBinding

/** 주행 중: 어두운 배경 + "운전 중" 한 줄. 질문할 때만 문구와 큰 버튼을 보인다. */
class DrivingFragment : Fragment() {

    private var _binding: FragmentDrivingBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDrivingBinding.inflate(inflater, container, false)
        return binding.root
    }

    fun showQuestion(text: String) {
        val b = _binding ?: return
        b.status.isVisible = false
        b.question.text = text
        b.question.isVisible = true
    }

    /** 음성 인식이 안 될 때 쓰는 큰 '네 / 아니요' 버튼. */
    fun showAnswerButtons(onYes: () -> Unit, onNo: () -> Unit) {
        val b = _binding ?: return
        b.yes.setOnClickListener { onYes() }
        b.no.setOnClickListener { onNo() }
        b.answerButtons.isVisible = true
    }

    fun clearQuestion() {
        val b = _binding ?: return
        b.question.isVisible = false
        b.answerButtons.isVisible = false
        b.status.isVisible = true
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
