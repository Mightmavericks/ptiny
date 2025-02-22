package com.example.protegotinyever.mode;

import java.util.List;
import com.example.protegotinyever.tt.UserModel;

public interface UserListCallback {
    int rea = 1;
    void onUsersFetched(List<UserModel> users);
}
