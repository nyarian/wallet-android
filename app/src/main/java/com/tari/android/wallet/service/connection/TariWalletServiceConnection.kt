/**
 * Copyright 2020 The Tari Project
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the
 * following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above
 * copyright notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of
 * its contributors may be used to endorse or promote products
 * derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.tari.android.wallet.service.connection

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.tari.android.wallet.service.TariWalletService
import com.tari.android.wallet.service.WalletService

class TariWalletServiceConnection(private val context: Context) : ViewModel(), ServiceConnection {

    private val _connection = MutableLiveData<ServiceConnectionState>()
    val connection: LiveData<ServiceConnectionState> get() = _connection
    val currentState get() = _connection.value!!

    init {
        _connection.value = ServiceConnectionState(ServiceConnectionStatus.NOT_YET_CONNECTED, null)
        val bindIntent = Intent(context, WalletService::class.java)
        context.bindService(bindIntent, this, Context.BIND_AUTO_CREATE)
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        _connection.value = ServiceConnectionState(ServiceConnectionStatus.DISCONNECTED, null)
    }

    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        _connection.value = ServiceConnectionState(
            ServiceConnectionStatus.CONNECTED,
            TariWalletService.Stub.asInterface(service)
        )
    }

    override fun onCleared() = context.unbindService(this)

    enum class ServiceConnectionStatus {
        NOT_YET_CONNECTED,
        CONNECTED,
        DISCONNECTED
    }

    data class ServiceConnectionState(
        val status: ServiceConnectionStatus,
        val service: TariWalletService?
    )

    class TariWalletServiceConnectionFactory(private val context: Context) :
        ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel?> create(modelClass: Class<T>): T {
            require(modelClass === TariWalletServiceConnection::class.java) { "Wrong class" }
            return TariWalletServiceConnection(context) as T
        }

    }

}

