package com.bougie.annie.familyshoppinglist;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.appinvite.AppInviteInvitation;
import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.crash.FirebaseCrash;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;

import java.util.ArrayList;
import java.util.Arrays;

public class MainActivity extends AppCompatActivity
        implements GoogleApiClient.OnConnectionFailedListener {

    public static final String ANONYMOUS = "anonymous";
    public static final int REQUEST_INVITE = 1;

    private int _number_of_items = 5;

    private ArrayList _arrayList;
    private ArrayAdapter _arrayAdapter;
    private EditText _inputItem;
    private SharedPreferences _sharedPreferences;
    private String _username;
    private String _photoUrl;
    private GoogleApiClient _googleApiClient;

    private static final String TAG = "MainActivity";

    // Firebase declarations
    FirebaseAuth _firebaseAuth;
    FirebaseUser _firebaseUser;
    private ChildEventListener _databaseEventListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        _sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        // Set default username is anonymous.
        _username = ANONYMOUS;

        _googleApiClient = new GoogleApiClient.Builder(this)
                .enableAutoManage(this /* FragmentActivity */, this /* OnConnectionFailedListener */)
                .addApi(Auth.GOOGLE_SIGN_IN_API)
                .build();

        _databaseEventListener = new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot dataSnapshot, String s) {
                Log.d("FB added data: ", dataSnapshot.getKey() + " " + dataSnapshot.getValue());
                ShoppingItem shoppingItem = dataSnapshot.getValue(ShoppingItem.class);
                addNewShoppingListItem(shoppingItem.item);
            }

            @Override
            public void onChildChanged(DataSnapshot dataSnapshot, String s) {
                Log.d("FB item changed", "honey badger don't care");
            }

            @Override
            public void onChildRemoved(DataSnapshot dataSnapshot) {
                Log.d("FB removed data: ", dataSnapshot.getKey() + " " + dataSnapshot.getValue());
                ShoppingItem shoppingItem = dataSnapshot.getValue(ShoppingItem.class);
                int position = _arrayList.indexOf(shoppingItem.item);
                if (position >= 0) {
                    removeShoppingListItem(position);
                }
            }

            @Override
            public void onChildMoved(DataSnapshot dataSnapshot, String s) {
                Log.d("FB item moved", "honey badger don't care");
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.d("FB data error: ", databaseError.getMessage());
            }
        };


        final ListView listView = (ListView) findViewById(R.id.listv);
        String[] items = {};
        _arrayList = new ArrayList(Arrays.asList(items));
        _arrayAdapter = new ArrayAdapter(this, R.layout.list_item, R.id.txtItem, _arrayList);
        listView.setAdapter(_arrayAdapter);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                AlertDialog.Builder adb = new AlertDialog.Builder(MainActivity.this);
                adb.setTitle("Remove?");
                final String itemText = listView.getItemAtPosition(i).toString();
                adb.setMessage("Are you sure you want to remove " + itemText);
                final int positionToRemove = i;
                adb.setNegativeButton("No", null);
                adb.setPositiveButton("Yes", new AlertDialog.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        removeShoppingListItem(positionToRemove);

                    }
                });
                adb.show();
            }
        });

        _inputItem = (EditText) findViewById(R.id.txtInput);
        Button addButton = (Button) findViewById(R.id.btnAddItem);
        addButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String newItem = _inputItem.getText().toString();
                addNewShoppingListItem(newItem);
                _inputItem.setText("");
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        // An unresolvable error has occurred and Google APIs (including Sign-In) will not
        // be available.
        Log.d(TAG, "onConnectionFailed:" + connectionResult);
        Toast.makeText(this, "Google Play Services error.", Toast.LENGTH_SHORT).show();
    }

    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case R.id.sign_out_menu:
                _firebaseAuth.signOut();
                Auth.GoogleSignInApi.signOut(_googleApiClient);
                _username = ANONYMOUS;
                startActivity(new Intent(this, SigninActivity.class));
                return true;
            case R.id.fresh_config_menu:
                Toast.makeText(this, "You have selected Fetch Config Menu", Toast.LENGTH_SHORT).show();
                return true;
            case R.id.invite_menu:
                Toast.makeText(this, "You have selected Invite Menu", Toast.LENGTH_SHORT).show();
                return true;
            case R.id.crash_menu:
                Toast.makeText(this, "You have selected Cause Crash Menu", Toast.LENGTH_SHORT).show();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void addNewShoppingListItem(String item) {
        _arrayList.add(item);
        _arrayAdapter.notifyDataSetChanged();
    }

    private void removeShoppingListItem(int position) {
        _arrayList.remove(position);
        _arrayAdapter.notifyDataSetChanged();
    }

    private void crashApp() {
        try {
            throw new RuntimeException("Testing crash reporting");
        } catch (Exception exception) {
            // log crash here
            throw exception;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_INVITE) {
            if (resultCode == RESULT_OK) {
                Bundle payload = new Bundle();

                Toast.makeText(this, "Invite sent", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Invite send failed", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
