package de.tum.in.tumcampusapp.component.ui.cafeteria.activity;

import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.text.SpannableString;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import org.joda.time.DateTime;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProviders;
import androidx.viewpager.widget.ViewPager;
import de.tum.in.tumcampusapp.R;
import de.tum.in.tumcampusapp.component.other.generic.activity.ActivityForDownloadingExternal;
import de.tum.in.tumcampusapp.component.other.locations.TumLocationManager;
import de.tum.in.tumcampusapp.component.ui.cafeteria.CafeteriaMenuInflater;
import de.tum.in.tumcampusapp.component.ui.cafeteria.details.CafeteriaDetailsSectionsPagerAdapter;
import de.tum.in.tumcampusapp.component.ui.cafeteria.details.CafeteriaViewModel;
import de.tum.in.tumcampusapp.component.ui.cafeteria.interactors.FetchBestMatchMensaInteractor;
import de.tum.in.tumcampusapp.component.ui.cafeteria.model.Cafeteria;
import de.tum.in.tumcampusapp.component.ui.cafeteria.repository.CafeteriaLocalRepository;
import de.tum.in.tumcampusapp.component.ui.cafeteria.repository.CafeteriaRemoteRepository;
import de.tum.in.tumcampusapp.utils.Const;
import de.tum.in.tumcampusapp.utils.Utils;
import de.tum.in.tumcampusapp.utils.ui.Dialogs;

/**
 * Lists all dishes at selected cafeteria
 * <p>
 * OPTIONAL: Const.CAFETERIA_ID set in incoming bundle (cafeteria to show)
 */
public class CafeteriaActivity extends ActivityForDownloadingExternal
        implements AdapterView.OnItemSelectedListener {

    private static final int NONE_SELECTED = -1;

    private CafeteriaViewModel cafeteriaViewModel;
    private List<Cafeteria> mCafeterias = new ArrayList<>();

    @Inject
    TumLocationManager tumLocationManager;

    @Inject
    FetchBestMatchMensaInteractor bestMatchMensaInteractor;

    @Inject
    CafeteriaLocalRepository localRepository;

    @Inject
    CafeteriaRemoteRepository remoteRepository;

    private ArrayAdapter<Cafeteria> adapter;
    private CafeteriaDetailsSectionsPagerAdapter sectionsPagerAdapter;
    private Spinner spinner;

    public CafeteriaActivity() {
        super(Const.CAFETERIAS, R.layout.activity_cafeteria);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // TODO: Inject
        getAppComponent().inject(this);

        ViewPager viewPager = findViewById(R.id.pager);
        viewPager.setOffscreenPageLimit(50);

        adapter = createArrayAdapter();

        spinner = findViewById(R.id.spinnerToolbar);
        spinner.setAdapter(adapter);
        spinner.setOnItemSelectedListener(this);

        initCafeteriaSpinner();

        sectionsPagerAdapter = new CafeteriaDetailsSectionsPagerAdapter(getSupportFragmentManager());

        // TODO: In the future, these should be injected
        CafeteriaViewModel.Factory factory = new CafeteriaViewModel.Factory(
                bestMatchMensaInteractor, localRepository, remoteRepository);
        cafeteriaViewModel = ViewModelProviders.of(this, factory).get(CafeteriaViewModel.class);

        cafeteriaViewModel.getCafeterias().observe(this, this::updateCafeteria);
        cafeteriaViewModel.getSelectedCafeteria().observe(this, this::onNewCafeteriaSelected);
        cafeteriaViewModel.getMenuDates().observe(this, this::updateSectionsPagerAdapter);

        cafeteriaViewModel.getError().observe(this, value -> {
            if (value) {
                showError(R.string.error_something_wrong);
            } else {
                showContentLayout();
            }
        });
    }

    private void updateCafeteria(List<Cafeteria> cafeterias) {
        mCafeterias = cafeterias;
        adapter.notifyDataSetChanged();
    }

    private ArrayAdapter<Cafeteria> createArrayAdapter() {
        return new ArrayAdapter<Cafeteria>(
                this, R.layout.simple_spinner_item_actionbar, android.R.id.text1, mCafeterias) {
            final LayoutInflater inflater = LayoutInflater.from(getContext());

            @Override
            public View getDropDownView(int position, View convertView, @NonNull ViewGroup parent) {
                View v = inflater.inflate(
                        R.layout.simple_spinner_dropdown_item_actionbar_two_line, parent, false);
                Cafeteria cafeteria = getItem(position);

                TextView name = v.findViewById(android.R.id.text1);
                TextView address = v.findViewById(android.R.id.text2);
                TextView distance = v.findViewById(R.id.distance);

                if (cafeteria != null) {
                    name.setText(cafeteria.getName());
                    address.setText(cafeteria.getAddress());
                    distance.setText(Utils.formatDistance(cafeteria.getDistance()));
                }

                return v;
            }
        };
    }

    @Override
    protected void onStart() {
        super.onStart();
        Location location = tumLocationManager.getCurrentOrNextLocation();
        cafeteriaViewModel.fetchCafeterias(location);
    }

    private void initCafeteriaSpinner() {
        final Intent intent = getIntent();
        int cafeteriaId;

        if (intent != null && intent.hasExtra(Const.MENSA_FOR_FAVORITEDISH)) {
            cafeteriaId = intent.getIntExtra(Const.MENSA_FOR_FAVORITEDISH, NONE_SELECTED);
            intent.removeExtra(Const.MENSA_FOR_FAVORITEDISH);
        } else if (intent != null && intent.hasExtra(Const.CAFETERIA_ID)) {
            cafeteriaId = intent.getIntExtra(Const.CAFETERIA_ID, 0);
        } else {
            // If we're not provided with a cafeteria ID, we choose the best matching cafeteria.
            cafeteriaId = cafeteriaViewModel.fetchBestMatchMensaId();
        }

        updateCafeteriaSpinner(cafeteriaId);
    }

    private void onNewCafeteriaSelected(Cafeteria cafeteria) {
        sectionsPagerAdapter.setCafeteriaId(cafeteria.getId());
        updateCafeteriaSpinner(cafeteria.getId());
        cafeteriaViewModel.fetchMenuDates();
    }

    private void updateCafeteriaSpinner(int cafeteriaId) {
        int selectedIndex = NONE_SELECTED;
        for (int i = 0; i < mCafeterias.size(); i++) {
            Cafeteria cafeteria = mCafeterias.get(i);
            if (cafeteriaId == NONE_SELECTED || cafeteriaId == cafeteria.getId()) {
                selectedIndex = i;
                break;
            }
        }

        if (selectedIndex != NONE_SELECTED) {
            spinner.setSelection(selectedIndex);
        }
    }

    /**
     * Switch cafeteria if a new cafeteria has been selected
     *
     * @param parent the parent view
     * @param pos    index of the new selection
     * @param id     id of the selected item
     */
    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
        Cafeteria selected = mCafeterias.get(pos);
        cafeteriaViewModel.updateSelectedCafeteria(selected);
    }

    private void updateSectionsPagerAdapter(List<DateTime> menuDates) {
        sectionsPagerAdapter.update(menuDates);
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        // Don't change anything
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_section_fragment_cafeteria_details, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_ingredients:
                showIngredientsInfo();
                break;
            case R.id.action_settings:
                startActivity(new Intent(this, CafeteriaNotificationSettingsActivity.class));
                break;
        }

        return super.onOptionsItemSelected(item);
    }

    private void showIngredientsInfo() {
        // Build a alert dialog containing the mapping of ingredients to the numbers
        String ingredients = getString(R.string.cafeteria_ingredients);
        SpannableString message = CafeteriaMenuInflater.menuToSpan(this, ingredients);
        Dialogs.showConfirm(this, R.string.action_ingredients, message);
    }

}