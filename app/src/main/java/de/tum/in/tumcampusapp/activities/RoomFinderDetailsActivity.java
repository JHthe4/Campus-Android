package de.tum.in.tumcampusapp.activities;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.view.Menu;
import android.view.MenuItem;

import com.google.common.base.Optional;

import java.io.File;
import java.io.IOException;
import java.util.List;

import de.tum.in.tumcampusapp.R;
import de.tum.in.tumcampusapp.activities.generic.ActivityForLoadingInBackground;
import de.tum.in.tumcampusapp.api.TUMCabeClient;
import de.tum.in.tumcampusapp.auxiliary.NetUtils;
import de.tum.in.tumcampusapp.auxiliary.Utils;
import de.tum.in.tumcampusapp.fragments.ImageViewTouchFragment;
import de.tum.in.tumcampusapp.fragments.WeekViewFragment;
import de.tum.in.tumcampusapp.models.tumcabe.RoomFinderMap;
import de.tum.in.tumcampusapp.models.tumcabe.RoomFinderRoom;
import de.tum.in.tumcampusapp.models.tumo.Geo;
import de.tum.in.tumcampusapp.tumonline.TUMRoomFinderRequest;

/**
 * Displays the map regarding the searched room.
 */
public class RoomFinderDetailsActivity
        extends ActivityForLoadingInBackground<Void, Optional<File>>
        implements DialogInterface.OnClickListener {

    public static final String EXTRA_ROOM_INFO = "roomInfo";
    public static final String EXTRA_LOCATION = "location";

    private ImageViewTouchFragment mImage;

    private boolean mapsLoaded;
    private TUMRoomFinderRequest request;
    private NetUtils net;

    private RoomFinderRoom room;
    private String mapId = "";
    private List<RoomFinderMap> mapsList;
    private boolean infoLoaded;
    private Fragment fragment;

    private AsyncTask<String, Void, Optional<List<RoomFinderMap>>> mapListaSyncTask;

    public RoomFinderDetailsActivity() {
        super(R.layout.activity_roomfinderdetails);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        net = new NetUtils(this);

        mImage = ImageViewTouchFragment.newInstance();
        getSupportFragmentManager().beginTransaction().add(R.id.fragment_container, mImage).commit();

        room = (RoomFinderRoom) getIntent().getExtras().getSerializable(EXTRA_ROOM_INFO);
        if (room == null) {
            Utils.showToast(this, "No room information passed");
            this.finish();
            return;
        }

        request = new TUMRoomFinderRequest(this);

        startLoading();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_roomfinder_detail, menu);
        MenuItem switchMap = menu.findItem(R.id.action_switch_map);
        switchMap.setVisible(!"10".equals(mapId) && mapsLoaded && fragment == null);
        MenuItem timetable = menu.findItem(R.id.action_room_timetable);
        timetable.setVisible(infoLoaded);
        timetable.setIcon(fragment == null ? R.drawable.ic_room_timetable : R.drawable.ic_action_map);
        menu.findItem(R.id.action_directions).setVisible(infoLoaded && fragment == null);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int i = item.getItemId();
        if (i == R.id.action_room_timetable) {
            getRoomTimetable();
            supportInvalidateOptionsMenu();
            return true;
        } else if (i == R.id.action_directions) {
            getDirections();
            return true;
        } else if (i == R.id.action_switch_map) {
            showMapSwitch();
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onBackPressed() {
        // Remove fragment with room timetable if present and show map again
        if (fragment != null) {
            getRoomTimetable();
            supportInvalidateOptionsMenu();
            return;
        }

        super.onBackPressed();
    }

    private void getRoomTimetable() {
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        // Remove if fragment is already present
        if (fragment != null) {
            ft.replace(R.id.fragment_container, mImage);
            ft.commit();
            fragment = null;
            return;
        }
        String roomApiCode = room.getRoom_id();
        fragment = WeekViewFragment.newInstance(roomApiCode);
        ft.replace(R.id.fragment_container, fragment);
        ft.commit();
    }

    private void showMapSwitch() {
        CharSequence[] list = new CharSequence[mapsList.size()];
        int curPos = 0;
        for (int i = 0; i < mapsList.size(); i++) {
            list[i] = mapsList.get(i).getDescription();
            if (mapsList.get(i).getMap_id().equals(mapId)) {
                curPos = i;
            }
        }
        new AlertDialog.Builder(this).setSingleChoiceItems(list, curPos, this).show();
    }

    @Override
    public void onClick(DialogInterface dialog, int whichButton) {
        dialog.dismiss();
        int selectedPosition = ((AlertDialog) dialog).getListView().getCheckedItemPosition();
        mapId = mapsList.get(selectedPosition).getMap_id();
        startLoading();
    }

    private void getDirections() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final Optional<Geo> geo = request.fetchCoordinates(room.getArch_id());
                if (!geo.isPresent()) {
                    Utils.showToastOnUIThread(RoomFinderDetailsActivity.this, R.string.no_map_available);
                    return;
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // Build get directions intent and see if some app can handle it
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + geo.get().getLatitude() + ',' + geo.get().getLongitude()));
                        List<ResolveInfo> pkgAppsList = getApplicationContext().getPackageManager().queryIntentActivities(intent, PackageManager.GET_RESOLVED_FILTER);

                        // If some app can handle this intent start it
                        if (!pkgAppsList.isEmpty()) {
                            startActivity(intent);
                            return;
                        }

                        // If no app is capable of opening it link to google maps market entry
                        try {
                            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.apps.maps")));
                        } catch (ActivityNotFoundException e) {
                            Utils.log(e);
                            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("http://play.google.com/store/apps/details?id=com.google.android.apps.maps")));
                        }
                    }
                });
            }
        }).start();
    }

    @Override
    protected Optional<File> onLoadInBackground(Void... arg) {
        String archId = room.getArch_id();
        String url;

        if (mapId == null || mapId.isEmpty()) {
            url = TUMRoomFinderRequest.fetchDefaultMap(archId);
        } else {
            url = TUMRoomFinderRequest.fetchMap(archId, mapId);
        }

        return net.downloadImage(url);
    }


    @Override
    protected void onLoadFinished(Optional<File> result) {
        if (!result.isPresent()) {
            if (NetUtils.isConnected(this)) {
                showErrorLayout();
            } else {
                showNoInternetLayout();
            }
            return;
        }
        infoLoaded = true;
        supportInvalidateOptionsMenu();

        //Update the fragment
        mImage = ImageViewTouchFragment.newInstance(result.get());
        getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container, mImage).commit();

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(room.getInfo());
            getSupportActionBar().setSubtitle(room.getAddress());
        }

        showLoadingEnded();

        startLoadingMapList(room.getArch_id());
    }

    private void onMapListLoadFinished(Optional<List<RoomFinderMap>> result){
        if(!result.isPresent()){
            if (NetUtils.isConnected(this)) {
                showErrorLayout();
            } else {
                showNoInternetLayout();
            }
            return;
        }

        mapsList = result.get();
        if (mapsList.size() > 1) {
            mapsLoaded = true;
        }

        supportInvalidateOptionsMenu();
    }

    private Optional<List<RoomFinderMap>> onLoadMapListInBackground(String archId){
        try {
            Optional<List<RoomFinderMap>> data =
                    Optional.of(TUMCabeClient.getInstance(this).fetchAvailableMaps(archId));
            if(data.isPresent()){
                return data;
            }
        } catch (IOException | NullPointerException e) {
            Utils.log(e);
        }

        return Optional.absent();
    }

    final void startLoadingMapList(String... params){
        if (mapListaSyncTask != null) {
            mapListaSyncTask.cancel(true);
        }

        mapListaSyncTask = new AsyncTask<String, Void, Optional<List<RoomFinderMap>>>() {
            @Override
            protected void onPreExecute() {
                showLoadingStart();
            }

            @Override
            protected Optional<List<RoomFinderMap>> doInBackground(String... params) {
                return onLoadMapListInBackground(params[0]);
            }

            @Override
            protected void onPostExecute(Optional<List<RoomFinderMap>> result) {
                showLoadingEnded();
                onMapListLoadFinished(result);
                mapListaSyncTask = null;
            }
        };
        mapListaSyncTask.execute(params);
    }
}
