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

public class RequestsFragment extends Fragment {
    private RecyclerView recyclerView;
    private TextView noUsersText;
    private UserAdapter adapter;
    private List<UserModel> availableUsers = new ArrayList<>();
    private WebRTCClient webRTCClient;
    private UserAdapter.OnUserClickListener userClickListener;
    private List<UserModel> pendingUsers;
    private boolean isViewCreated = false;
    private ConnectionManager connectionManager;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        connectionManager = ConnectionManager.getInstance(requireContext());
    }

    public static RequestsFragment newInstance() {
        return new RequestsFragment();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_requests, container, false);
        recyclerView = view.findViewById(R.id.requestsRecyclerView);
        noUsersText = view.findViewById(R.id.noRequestsText);
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
        noUsersText = null;
        adapter = null;
    }

    private void setupRecyclerView() {
        if (recyclerView != null && getContext() != null) {
            recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
            adapter = new UserAdapter(availableUsers, userClickListener, true);
            recyclerView.setAdapter(adapter);
        }
    }

    public void setWebRTCClient(WebRTCClient client) {
        this.webRTCClient = client;
    }

    public void setUserClickListener(UserAdapter.OnUserClickListener listener) {
        this.userClickListener = listener;
        if (adapter != null && recyclerView != null) {
            adapter = new UserAdapter(availableUsers, listener, true);
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

        availableUsers.clear();
        // Only show users that have never been connected
        for (UserModel user : users) {
            if (!connectionManager.isUserConnected(user.getUsername())) {
                availableUsers.add(user);
            }
        }
        
        getActivity().runOnUiThread(() -> {
            if (isViewCreated && isAdded()) {
                if (noUsersText != null) {
                    noUsersText.setVisibility(availableUsers.isEmpty() ? View.VISIBLE : View.GONE);
                }
                if (recyclerView != null) {
                    recyclerView.setVisibility(availableUsers.isEmpty() ? View.GONE : View.VISIBLE);
                }
                if (adapter != null) {
                    adapter.notifyDataSetChanged();
                }
            }
        });
    }
} 