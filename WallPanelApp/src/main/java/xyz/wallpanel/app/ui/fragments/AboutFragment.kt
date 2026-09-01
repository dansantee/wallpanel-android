/*
 * Copyright (c) 2022 WallPanel
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed 
 * under the License is distributed on an "AS IS" BASIS, 
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
 * See the License for the specific language governing permissions and 
 * limitations under the License.
 */

package xyz.wallpanel.app.ui.fragments

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.navigation.Navigation
import xyz.wallpanel.app.R
import xyz.wallpanel.app.ui.activities.SettingsActivity
import xyz.wallpanel.app.databinding.FragmentAboutBinding

import timber.log.Timber

class AboutFragment : Fragment() {


    private lateinit var binding: FragmentAboutBinding
    private var versionNumber: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentAboutBinding.inflate(inflater, container, false);
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        // Set title bar
        if((activity as SettingsActivity).supportActionBar != null) {
            (activity as SettingsActivity).supportActionBar!!.setDisplayHomeAsUpEnabled(true)
            (activity as SettingsActivity).supportActionBar!!.setDisplayShowHomeEnabled(true)
            (activity as SettingsActivity).supportActionBar!!.title = (getString(R.string.pref_about_title))
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        if (id == android.R.id.home) {
            view?.let { Navigation.findNavController(it).navigate(R.id.settings_action) }
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        try {
            val packageInfo = requireActivity().packageManager.getPackageInfo(requireActivity().packageName, 0)
            versionNumber = " v" + packageInfo.versionName
            binding.versionName.text = versionNumber
        } catch (e: PackageManager.NameNotFoundException) {
            Timber.e(e.message)
        }

        // This fork has no Play listing, no support site, no privacy policy and no maintainer
        // mailbox, so the buttons that pointed at the original project's are hidden rather than
        // left to open someone else's pages.
        binding.sendFeedbackButton.visibility = View.GONE
        binding.rateApplicationButton.visibility = View.GONE
        binding.privacyPolicyButton.visibility = View.GONE
        binding.githubButton.setOnClickListener { showGitHub() }
        binding.supportButton.setOnClickListener { showSupport() }
    }

    private fun showSupport() {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SUPPORT_URL)))
    }

    private fun showGitHub() {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_URL)))
    }

    companion object {
        // This fork's own repository. Upstream is archived and its site, Play listing and
        // maintainer mailbox are not ours to send users to.
        const val PROJECT_URL = "https://github.com/dansantee/wallpanel-android"
        const val SUPPORT_URL: String = PROJECT_URL
        const val GITHUB_URL = PROJECT_URL

        fun newInstance(): AboutFragment {
            return AboutFragment()
        }
    }
}