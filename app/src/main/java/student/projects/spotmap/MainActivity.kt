package student.projects.spotmap

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.PopupMenu
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import student.projects.spotmap.ui.addspot.AddSpotFragment


class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)

        // Default fragment = Home
        if (savedInstanceState == null) {
            loadFragment(HomeFragment())
        }

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    loadFragment(HomeFragment())
                    true
                }
                R.id.nav_map -> {
                    loadFragment(MapFragment())
                    true
                }
                R.id.nav_add_spot -> {
                    loadFragment(AddSpotFragment())
                    true
                }
                R.id.navigation_more -> {
                    showMorePopup(bottomNav)
                    true
                }
                else -> false
            }
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    private fun showMorePopup(bottomNav: BottomNavigationView) {
        val moreItemView = bottomNav.findViewById<android.view.View>(R.id.navigation_more)
        val popup = PopupMenu(this, moreItemView)
        popup.menuInflater.inflate(R.menu.more_menu, popup.menu)
        popup.setOnMenuItemClickListener { menuItem ->
            handleMoreMenuClick(menuItem)
            true
        }
        popup.show()
    }

    private fun handleMoreMenuClick(menuItem: MenuItem) {
        when (menuItem.itemId) {
            R.id.menu_profile -> loadFragment(ProfileFragment())
            R.id.menu_store -> loadFragment(StoreFragment())
            R.id.menu_settings -> {
                loadFragment(student.projects.spotmap.ui.settings.SettingsFragment())
            }

        }
    }

}
