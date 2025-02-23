package com.example.protegotinyever.act;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.protegotinyever.R;
import com.example.protegotinyever.service.ConnectionManager;
import com.example.protegotinyever.tt.UserAdapter;
import com.example.protegotinyever.tt.UserModel;
import com.example.protegotinyever.webrtc.WebRTCClient;

import java.util.ArrayList;
import java.util.List;

public class ChatsFragment extends Fragment {
    private RecyclerView recyclerView;
    private TextView noChatsText;
    private UserAdapter adapter;
    private List<UserModel> connectedUsers = new ArrayList<>();
    private WebRTCClient webRTCClient;
    private UserAdapter.OnUserClickListener userClickListener;
    private boolean isViewCreated = false;
    private List<UserModel> pendingUsers;
    private ConnectionManager connectionManager;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        connectionManager = ConnectionManager.getInstance(requireContext());
    }

    public static ChatsFragment newInstance() {
        return new ChatsFragment();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_chats, container, false);
        recyclerView = view.findViewById(R.id.chatsRecyclerView);
        noChatsText = view.findViewById(R.id.noChatsText);
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        isViewCreated = true;
        setupRecyclerView();
        if (pendingUsers != null) {
            updateUsers(pendingUsers);
            pendingUsers = null;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        isViewCreated = false;
        recyclerView = null;
        noChatsText = null;
        adapter = null;
    }

    private void setupRecyclerView() {
        if (recyclerView != null && getContext() != null) {
            recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
            adapter = new UserAdapter(connectedUsers, userClickListener, false);
            recyclerView.setAdapter(adapter);
        }
    }

    public void setWebRTCClient(WebRTCClient client) {
        this.webRTCClient = client;
    }

    public void setUserClickListener(UserAdapter.OnUserClickListener listener) {
        this.userClickListener = listener;
        if (adapter != null && recyclerView != null) {
            adapter = new UserAdapter(connectedUsers, listener, false);
            recyclerView.setAdapter(adapter);
        }
    }

    public void updateUsers(List<UserModel> users) {
        if (!isViewCreated) {
            pendingUsers = users;
            return;
        }

        if (getActivity() == null || !isAdded()) {
            return;
        }

        connectedUsers.clear();
        // Add all users that were ever connected
        for (UserModel user : users) {
            if (connectionManager.isUserConnected(user.getUsername())) {
                connectedUsers.add(user);
            }
        }
        
        getActivity().runOnUiThread(() -> {
            if (isViewCreated && isAdded()) {
                if (noChatsText != null) {
                    noChatsText.setVisibility(connectedUsers.isEmpty() ? View.VISIBLE : View.GONE);
                }
                if (recyclerView != null) {
                    recyclerView.setVisibility(connectedUsers.isEmpty() ? View.GONE : View.VISIBLE);
                }
                if (adapter != null) {
                    adapter.notifyDataSetChanged();
                }
            }
        });
    }
} 