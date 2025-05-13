package com.nicksunday.boardgameassistant

import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.nicksunday.boardgameassistant.data.repository.FirestoreRepository
import com.nicksunday.boardgameassistant.databinding.ActivityMainBinding
import com.nicksunday.boardgameassistant.ui.MainViewModel
import com.nicksunday.boardgameassistant.ui.MainViewModelFactory
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var authManager: AuthManager
    val firestoreRepo = FirestoreRepository()

    private val viewModel: MainViewModel by viewModels {
        MainViewModelFactory(firestoreRepo)
    }

    private val navController by lazy {
        val navHost = supportFragmentManager
            .findFragmentById(R.id.main_frame) as NavHostFragment
        navHost.navController
    }

    fun hideKeyboard(view: View) {
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(view.windowToken, 0)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        // 1) Init AuthManager and grab the current user immediately
        authManager = AuthManager()
        val currentUser = authManager.getCurrentUser()

        // 2) Dynamically pick startDestination
        val navHost = supportFragmentManager
            .findFragmentById(R.id.main_frame) as NavHostFragment
        val navGraph = navHost.navController
            .navInflater
            .inflate(R.navigation.nav_graph)
            .apply {
                setStartDestination(
                    if (currentUser == null) R.id.loginFragment
                    else R.id.libraryFragment
                )
            }
        navHost.navController.graph = navGraph

        // 3) Wire up toolbar & drawer
        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.loginFragment,
                R.id.libraryFragment
            ),
            binding.drawerLayout
        )
        setupActionBarWithNavController(navHost.navController, appBarConfiguration)
        binding.navView.setupWithNavController(navHost.navController)

        navHost.navController
            .addOnDestinationChangedListener { _, dst, _ ->
                val onLogin = dst.id == R.id.loginFragment
                binding.toolbar.visibility = if (onLogin) View.GONE else View.VISIBLE
                binding.drawerLayout.setDrawerLockMode(
                    if (onLogin) DrawerLayout.LOCK_MODE_LOCKED_CLOSED
                    else DrawerLayout.LOCK_MODE_UNLOCKED
                )
            }

        // 4) React to login/logout events
        authManager.observeUser().observe(this) { user ->
            if (user == null) {
                Log.d("MainActivity", "→ not logged in, resetting graph to Login")
                resetNavGraph(R.id.loginFragment)
            } else {
                Log.d("MainActivity", "✔ logged in as ${user.email}, resetting graph to Library")
                resetNavGraph(R.id.libraryFragment)

                // now that graph is Library-first, safe to load Firestore
                val repo = FirestoreRepository()
                lifecycleScope.launch {
                    repo.addUserIfNotExists(user.displayName ?: "Unknown")
                    viewModel.refreshLibrary()
                    viewModel.loadPlayers(repo)
                }

                // re‑hook your drawer listener
                binding.navView.setNavigationItemSelectedListener { item ->
                    when (item.itemId) {
                        R.id.nav_logout -> {
                            authManager.logout(this)
                            binding.drawerLayout.closeDrawers()
                            true
                        }

                        else -> {
                            NavigationUI.onNavDestinationSelected(item, navController)
                            binding.drawerLayout.closeDrawers()
                            true
                        }
                    }
                }
            }
        }
    }


    private fun resetNavGraph(startDestId: Int) {
        val navHost = supportFragmentManager
            .findFragmentById(R.id.main_frame) as NavHostFragment
        val graph = navHost.navController
            .navInflater
            .inflate(R.navigation.nav_graph)
            .apply {
                setStartDestination(startDestId)
            }
        navHost.navController.graph = graph
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp(appBarConfiguration)
                || super.onSupportNavigateUp()
    }
}
