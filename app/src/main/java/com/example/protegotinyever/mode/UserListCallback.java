package com.example.protegotinyever.mode;

import com.example.protegotinyever.tt.UserModel;
import java.util.List;

public interface UserListCallback {
    void onUsersFetched(List<UserModel> users);
}
