package com.github.livingwithhippos.unchained.user.view

import android.Manifest
import android.content.Intent
import android.view.View
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.lifecycle.lifecycleScope
import coil.load
import com.github.livingwithhippos.unchained.R
import com.github.livingwithhippos.unchained.base.UnchainedFragment
import com.github.livingwithhippos.unchained.data.model.TorboxAccount
import com.github.livingwithhippos.unchained.data.model.User
import com.github.livingwithhippos.unchained.databinding.FragmentUserProfileBinding
import com.github.livingwithhippos.unchained.settings.view.SettingsActivity
import com.github.livingwithhippos.unchained.statemachine.authentication.FSMAuthenticationState
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

/** A simple [UnchainedFragment] subclass. Shows a user profile details. */
@AndroidEntryPoint
class UserProfileFragment : UnchainedFragment() {

    private val DATE_FORMAT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())


    private var _binding: FragmentUserProfileBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding
        get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        // Inflate the layout for this fragment
        _binding = FragmentUserProfileBinding.inflate(inflater, container, false)
        val view = binding.root

        // Real-Debrid furniture: no avatar, no points economy, and both Settings buttons opened the
        // same screen. Hidden rather than removed so the layout keeps its shape.
        binding.ivProfilePic.visibility = View.GONE
        binding.pointsBar.visibility = View.GONE
        binding.tvPoints.visibility = View.GONE
        binding.bAccount.visibility = View.GONE

        val user: User? = activityViewModel.getCachedUser()
        val account: TorboxAccount? = activityViewModel.getCachedAccount()
        if (account != null) populateAccountView(account)
        if (user == null && account == null) {
            binding.tvName.text = getString(R.string.torbox_connected)
            binding.tvMail.text = getString(R.string.torbox_profile_description)
            binding.tvPremium.text = getString(R.string.torbox_ready)
            binding.tvPremiumDays.text = ""
            binding.tvPoints.text = ""
            binding.pointsBar.progress = 0
        } else {
            populateUserView(user)
        }
        lifecycleScope.launch {
            if (activityViewModel.isTokenPrivate()) {
                binding.tvLoginDescription.text = getString(R.string.login_type_private)
            } else {
                binding.tvLoginDescription.text = getString(R.string.login_type_open)
            }
        }

        activityViewModel.fetchUser()
        activityViewModel.accountLiveData.observe(viewLifecycleOwner) { event ->
            if (_binding == null) return@observe
            event.peekContent()?.let { populateAccountView(it) }
        }

        activityViewModel.userLiveData.observe(viewLifecycleOwner) {
            if (_binding == null) return@observe
            populateUserView(it.peekContent())
            lifecycleScope.launch {
                if (activityViewModel.isTokenPrivate()) {
                    binding.tvLoginDescription.text = getString(R.string.login_type_private)
                } else {
                    binding.tvLoginDescription.text = getString(R.string.login_type_open)
                }
            }
        }

        binding.bAccount.setOnClickListener {
            binding.bSettings.performClick()
        }

        activityViewModel.fsmAuthenticationState.observe(viewLifecycleOwner) {
            if (it != null) {
                when (it.peekContent()) {
                    is FSMAuthenticationState.WaitingUserAction -> {
                        // an error occurred, check it and eventually go back to the start fragment
                        val action = UserProfileFragmentDirections.actionUserToStartFragment()
                        safeNavigate(action)
                    }

                    FSMAuthenticationState.StartNewLogin -> {
                        // the user reset the login, go to the auth fragment
                        val action =
                            UserProfileFragmentDirections.actionUserToAuthenticationFragment()
                        safeNavigate(action)
                    }

                    FSMAuthenticationState.AuthenticatedOpenToken,
                    FSMAuthenticationState.AuthenticatedPrivateToken,
                    FSMAuthenticationState.RefreshingOpenToken -> {
                        // managed by activity
                    }

                    FSMAuthenticationState.CheckCredentials -> {
                        // shouldn't matter
                    }

                    FSMAuthenticationState.Start,
                    FSMAuthenticationState.WaitingToken,
                    FSMAuthenticationState.WaitingUserConfirmation -> {
                        // shouldn't happen
                    }
                }
            }
        }

        binding.bSettings.setOnClickListener {
            val intent = Intent(requireContext(), SettingsActivity::class.java)
            startActivity(intent)
        }

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.POST_NOTIFICATIONS,
                ) != PermissionChecker.PERMISSION_GRANTED
        ) {
            activityViewModel.requireNotificationPermissions()
        }

        return view
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /**
     * Renders the TorBox account summary. The email is deliberately not shown: this screen is
     * screenshot-friendly by design.
     */
    fun populateAccountView(account: TorboxAccount) {
        if (_binding == null) return
        binding.tvName.text =
            getString(
                if (account.isSubscribed) R.string.account_plan_subscribed
                else R.string.account_plan_free
            )
        val expiry = account.expiresAt?.let(::parseDate)
        binding.tvMail.text =
            if (expiry == null) ""
            else {
                val days = ChronoUnit.DAYS.between(LocalDate.now(), expiry).coerceAtLeast(0)
                getString(R.string.account_expires_format, expiry.format(DATE_FORMAT), days)
            }
        // Also set here: after a rotation or process death the cached account renders this path and
        // the static fallback (which writes the badge) is skipped.
        binding.tvPremium.text = getString(R.string.torbox_ready)
        binding.tvPremiumDays.text =
            buildList {
                account.createdAt?.let(::parseDate)?.let {
                    add(getString(R.string.account_member_since_format, it.format(DATE_FORMAT)))
                }
                if (account.torrentsDownloaded > 0)
                    add(
                        getString(
                            R.string.account_torrents_format,
                            account.torrentsDownloaded
                        )
                    )
            }.joinToString(" · ")
    }

    private fun parseDate(raw: String): LocalDate? =
        runCatching { OffsetDateTime.parse(raw).toLocalDate() }
            .recoverCatching { LocalDate.parse(raw.substringBefore('T')) }
            .getOrNull()

    fun populateUserView(user: User?) {
        if (_binding == null) return
        user?.let {
            binding.tvName.text = it.username
            binding.tvMail.text = it.email
            // todo: check https://coil-kt.github.io/coil/image_loaders/#caching
            binding.ivProfilePic.load(it.avatar) { crossfade(true) }
            if (it.premium > 0) {
                binding.tvPremium.text = getString(R.string.premium)
            } else {
                binding.tvPremium.text = getString(R.string.not_premium)
            }
            binding.tvPremiumDays.text =
                getString(R.string.premium_days_format, it.premium / 60 / 60 / 24)
            binding.tvPoints.text = getString(R.string.premium_points_format, it.points)
            binding.pointsBar.setProgressCompat(it.points, true)
        }
    }
}
