package com.example.wingman;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

public class Notes_Edit_Fragment extends FragmentStateAdapter {

    public Notes_Edit_Fragment(@NonNull FragmentActivity fa) {
        super(fa);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        return new Notes_Edit();
    }

    @Override
    public int getItemCount() {
        return 1;
    }
}
