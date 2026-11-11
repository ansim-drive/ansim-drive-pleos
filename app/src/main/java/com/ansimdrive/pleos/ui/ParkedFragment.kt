package com.ansimdrive.pleos.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import com.ansimdrive.pleos.R
import com.ansimdrive.pleos.data.WeeklyStatus
import com.ansimdrive.pleos.databinding.FragmentParkedBinding

/** 정차 중: 큰 글씨 요약 (오늘 주행거리·급제동·밤 운전·이번 주 상태, 가족 공유 상태). */
class ParkedFragment : Fragment() {

    private var _binding: FragmentParkedBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentParkedBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val args = requireArguments()
        val week = WeeklyStatus.valueOf(args.getString(ARG_WEEK, WeeklyStatus.CLEAR.name))
        binding.distance.text = "%.1f km".format(args.getDouble(ARG_DISTANCE))
        binding.hardBrakes.text = "${args.getInt(ARG_HARD_BRAKES)}회"
        binding.night.text = if (args.getBoolean(ARG_NIGHT)) "있음" else "없음"
        binding.week.text = "${week.icon} ${week.label}"
        binding.familySharing.text =
            getString(R.string.family_sharing, if (args.getBoolean(ARG_FAMILY_SHARING)) "켜짐" else "꺼짐")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_DISTANCE = "distance_km"
        private const val ARG_HARD_BRAKES = "hard_brakes"
        private const val ARG_NIGHT = "night"
        private const val ARG_WEEK = "week"
        private const val ARG_FAMILY_SHARING = "family_sharing"

        fun newInstance(
            distanceKm: Double,
            hardBrakes: Int,
            nightDriving: Boolean,
            week: WeeklyStatus,
            familySharing: Boolean,
        ) = ParkedFragment().apply {
            arguments = bundleOf(
                ARG_DISTANCE to distanceKm,
                ARG_HARD_BRAKES to hardBrakes,
                ARG_NIGHT to nightDriving,
                ARG_WEEK to week.name,
                ARG_FAMILY_SHARING to familySharing,
            )
        }
    }
}
