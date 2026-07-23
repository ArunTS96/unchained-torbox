package com.github.livingwithhippos.unchained.authentication.view

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.github.livingwithhippos.unchained.R
import com.github.livingwithhippos.unchained.authentication.viewmodel.AuthenticationViewModel
import com.github.livingwithhippos.unchained.base.UnchainedFragment
import com.github.livingwithhippos.unchained.databinding.FragmentAuthenticationBinding
import com.github.livingwithhippos.unchained.statemachine.authentication.FSMAuthenticationEvent
import com.github.livingwithhippos.unchained.statemachine.authentication.FSMAuthenticationState
import com.github.livingwithhippos.unchained.utilities.extension.getClipboardText
import com.github.livingwithhippos.unchained.utilities.extension.hideKeyboard
import com.github.livingwithhippos.unchained.utilities.extension.showToast
import dagger.hilt.android.AndroidEntryPoint

/** TorBox-only authentication screen. */
@AndroidEntryPoint
class AuthenticationFragment : UnchainedFragment() {

    private val viewModel: AuthenticationViewModel by viewModels()
    private var _binding: FragmentAuthenticationBinding? = null
    private val binding
        get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentAuthenticationBinding.inflate(inflater, container, false)

        binding.bPastePrivateCode.setOnClickListener {
            binding.tiPrivateCode.setText(getClipboardText(), TextView.BufferType.EDITABLE)
            binding.tiPrivateCode.hideKeyboard()
        }
        binding.bInsertPrivate.setOnClickListener { saveKey() }
        binding.tiPrivateCode.setOnFocusChangeListener { view, hasFocus ->
            if (!hasFocus) view.hideKeyboard()
        }

        activityViewModel.fsmAuthenticationState.observe(viewLifecycleOwner) { event ->
            when (event?.peekContent()) {
                FSMAuthenticationState.AuthenticatedPrivateToken,
                FSMAuthenticationState.AuthenticatedOpenToken -> {
                    findNavController().navigate(
                        AuthenticationFragmentDirections.actionAuthenticationToUser()
                    )
                }
                FSMAuthenticationState.StartNewLogin -> binding.tiPrivateCode.text?.clear()
                else -> Unit
            }
        }

        return binding.root
    }

    private fun saveKey() {
        val value = binding.tiPrivateCode.text?.toString()?.trim().orEmpty()
        if (value.length < 10) {
            context?.showToast(R.string.invalid_token)
            return
        }
        viewModel.saveApiKey(value)
        activityViewModel.transitionAuthenticationMachine(FSMAuthenticationEvent.OnPrivateToken)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
