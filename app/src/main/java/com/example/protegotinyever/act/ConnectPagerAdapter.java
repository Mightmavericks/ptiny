package com.example.protegotinyever.act;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

public class ConnectPagerAdapter extends FragmentStateAdapter {
    private final RequestsFragment requestsFragment;
    private final ChatsFragment chatsFragment;

    public ConnectPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
        requestsFragment = RequestsFragment.newInstance();
        chatsFragment = ChatsFragment.newInstance();
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        return position == 0 ? requestsFragment : chatsFragment;
    }

    @Override
    public int getItemCount() {
        return 2;
    }

    public RequestsFragment getRequestsFragment() {
        return requestsFragment;
    }

    public ChatsFragment getChatsFragment() {
        return chatsFragment;
    }
} 