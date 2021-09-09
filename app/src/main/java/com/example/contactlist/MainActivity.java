package com.example.contactlist;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static android.content.ContentValues.TAG;

public class MainActivity extends AppCompatActivity {
    RecyclerView recyclerView;
    ArrayList<ContactModel> arrayList = new ArrayList<ContactModel>();
    HashSet<ContactModel> contactsSet;
    MainAdapter adapter;
    Database database;
    String lastnumber = "0";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        recyclerView = findViewById(R.id.recycler_view);
        database = new Database(this, "contacts.sqlite", null, 1);
        database.queryData("CREATE TABLE IF NOT EXISTS Contacts(Id INTEGER PRIMARY KEY AUTOINCREMENT, Name String, Number String)");
        // Kiem tra quyen truy cap danh ba
        checkPermission();

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_contact_app, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.merge:
                this.arrayList = merge();
                adapter = new MainAdapter(this, arrayList);
                recyclerView.setAdapter(adapter);
                return true;
            case R.id.ReadContacts:
                readContacts();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public ArrayList<ContactModel> merge()
    {
        ArrayList<ContactModel> duplicateContacts = findDuplicateContactByPhoneNumber();

        for (ContactModel contactModel : duplicateContacts) {
            ArrayList<ContactModel> arr = getContactByPhoneNumber(contactModel.getNumber());
            for (ContactModel c : arr) {
                if (contactModel.getId() != c.getId()) {
                    deleteContact(c.getId());
                }
            }
        }

        return getContactList();
    }

    public void readContacts()
    {
        arrayList.clear();
        arrayList = getContactList();
        adapter = new MainAdapter(this, arrayList);
        recyclerView.setAdapter(adapter);
    }

    public boolean isDatabaseEmpty(Database db) {
        Cursor contactList = database.getData("SELECT COUNT(*) FROM Contacts");
        contactList.moveToFirst();
        int icount = contactList.getInt(0);
        Log.e("Table count", String.valueOf(icount));
        if(icount>0)
            return false;
        return  true;
    }

    private  void checkPermission(){
        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED)
            // Yeu cau quyen truy cap danh ba neu khong co quyen
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.READ_CONTACTS}, 100);
            else {

                readContactListFromPhone();   // cho nay la lay data tu danh ba trong may
                Log.e("get contact from phone", "run");
                if (!isDatabaseEmpty(database)) {                       // Check db trong k
                    database.queryData("DELETE FROM Contacts");     // Co du lieu thi xoa het
                }

                // Nap lai vao db data tu danh ba trong may
                for (int i = 0; i < arrayList.size(); i++)
                    database.queryData("INSERT INTO Contacts VALUES(null, '" + arrayList.get(i).getName() + "', '" + arrayList.get(i).getNumber() + "')");

                arrayList.clear();
                arrayList = getContactList();

                adapter = new MainAdapter(this, arrayList);
                recyclerView.setAdapter(adapter);

                for (int i = 0; i < arrayList.size(); i++)
                    Log.e("Id= "+arrayList.get(i).getId(), arrayList.get(i).getName() + ": " + arrayList.get(i).getNumber());


//                if (isDatabaseEmpty(database)) {
//                    Log.e("Db empty", "true");
//                    for (int i = 0; i < arrayList.size(); i++)
//                        database.queryData("INSERT INTO Contacts VALUES(null, '" + arrayList.get(i).getName() + "', '" + arrayList.get(i).getNumber() + "')");
//                } else {
//                    Log.e("Database empty", "false");
//
//                    //Xoa het nap lai de update voi contact trong may
//                    database.queryData("DELETE FROM Contacts");
//
//                    for (int i = 0; i < arrayList.size(); i++)
//                    database.queryData("INSERT INTO Contacts VALUES(null, '" + arrayList.get(i).getName() + "', '" + arrayList.get(i).getNumber() + "')");
//
//                    arrayList.clear();
//                    getContactListFromDb(0);
//                    adapter = new MainAdapter(this, arrayList);
//                    recyclerView.setAdapter(adapter);
//
//                    for (int i = 0; i < arrayList.size(); i++)
//                        Log.e("Id= "+arrayList.get(i).getId(), arrayList.get(i).getName() + ": " + arrayList.get(i).getNumber());
//                }
            }

    }

    private void readContactListFromPhone() {
        Uri uri = ContactsContract.Contacts.CONTENT_URI;

        // sort tăng dần theo tên A-Z
        String sort = ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME+" ASC";
        Cursor cursor = getContentResolver().query(
                uri,null,null,null, sort
        );

        // So contact trong danh ba lon hon 0
        if (cursor.getCount() > 0) {
            while(cursor.moveToNext()){
                //Get contact id
                List<String> nameContact = new ArrayList<String>();
                String id = cursor.getString(cursor.getColumnIndex(
                        ContactsContract.Contacts._ID
                ));
                //Get contact name
                String name = cursor.getString(cursor.getColumnIndex(
                        ContactsContract.Contacts.DISPLAY_NAME
                ));

                // Tạo phone uri
                Uri uriPhone = ContactsContract.CommonDataKinds.Phone.CONTENT_URI;
                // Tao selection uri
                String selection = ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " =?";

                Cursor phoneCursor = getContentResolver().query(
                        uriPhone, null, selection,new String[]{id}, null
                );

                if (phoneCursor.moveToNext()){
                    String number = phoneCursor.getString(phoneCursor.getColumnIndex(
                            ContactsContract.CommonDataKinds.Phone.NUMBER
                    ));

                    ContactModel model = new ContactModel();
                    model.setName(name);
                    model.setNumber(number);
                    arrayList.add(model);

                    phoneCursor.close();
                }
            }
            cursor.close();

        }
        //set data vao list view
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new MainAdapter(this, arrayList);
        recyclerView.setAdapter(adapter);
    }

    private ArrayList<ContactModel> getContactList() {
        ArrayList<ContactModel> contactList =  new ArrayList<>();
        Cursor cursor = database.getData("SELECT * FROM Contacts");
        while (cursor.moveToNext()) {
            int id = cursor.getInt(0);
            String name = cursor.getString(1);
            String phoneNumber = cursor.getString(2);

            contactList.add(new ContactModel(id, name, phoneNumber));
        }

        return contactList;
    }

    private ArrayList<ContactModel> getContactByPhoneNumber(String phoneNumber) {
        ArrayList<ContactModel> contactList = new ArrayList<>();
        Cursor cursor = database.getData("SELECT * FROM Contacts WHERE Number = " + phoneNumber);
        while (cursor.moveToNext()) {
            int id = cursor.getInt(0);
            String name = cursor.getString(1);

            contactList.add(new ContactModel(id, name, phoneNumber));
        }

        return contactList;
    }

    private ArrayList<ContactModel> findDuplicateContactByPhoneNumber() {
        ArrayList<ContactModel> duplicateContact = new ArrayList<>();
        Cursor cursor = database.getData("SELECT Id, Name, Number, COUNT(Number) FROM Contacts GROUP BY Number HAVING COUNT(Number) > 1");
        while (cursor.moveToNext()) {
            int id = cursor.getInt(0);
            String name = cursor.getString(1);
            String phoneNumber = cursor.getString(2);

            duplicateContact.add(new ContactModel(id, name, phoneNumber));
        }

        return duplicateContact;
    }

    private void addContact(ContactModel contactModel) {
        database.queryData("INSERT INTO Contacts VALUES(null, '" + contactModel.getName() + "', '" + contactModel.getName() + "')");
    }

    private void deleteContact(int id) {
        database.queryData("DELETE FROM Contacts WHERE Id = " + id);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100 && grantResults.length > 0 && grantResults[0]
            == PackageManager.PERMISSION_GRANTED){
            // khi duoc cap quyen lap tuc get contact list
            readContactListFromPhone();
        } else {
            // khi bi tu choi quyen truy cap danh ba
            Toast.makeText(MainActivity.this, "Permission Dennied.", Toast.LENGTH_SHORT).show();
            checkPermission();
        }
    }
}