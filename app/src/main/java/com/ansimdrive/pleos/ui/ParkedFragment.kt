package com.ansimdrive.pleos.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import com.ansimdrive.pleos.data.TripStats
import com.ansimdrive.pleos.databinding.FragmentParkedBinding

/** 정차 중: 큰 글씨 요약. */
class ParkedFragment : Fragment() {

    private var _binding: FragmentParkedBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentParkedBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val args = requireArguments()
        val night = if (args.getBoolean(ARG_NIGHT)) "있음" else "없음"
        binding.summary.text = "주행 거리   %.1f km\n급제동   %d회\n밤 운전   %s"
            .format(args.getDouble(ARG_DISTANCE), args.getInt(ARG_HARD_BRAKES), night)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_DISTANCE = "distance_km"
        private const val ARG_HARD_BRAKES = "hard_brakes"
        private const val ARG_NIGHT = "night"

        fun newInstance(stats: TripStats) = ParkedFragment().apply {
            arguments = bundleOf(
                ARG_DISTANCE to stats.distanceKm,
                ARG_HARD_BRAKES to stats.hardBrakes,
                ARG_NIGHT to stats.nightDriving,
            )
        }
    }
}
