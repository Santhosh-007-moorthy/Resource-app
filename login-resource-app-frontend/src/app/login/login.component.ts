import { CommonModule } from '@angular/common';
import { Component, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

@Component({
  selector: 'app-login',
  templateUrl: './login.component.html',
  styleUrl: './login.component.css',
  imports: [CommonModule, FormsModule]
})
export class LoginComponent {
  username = '';
  password = '';
  errorMessage = signal('');
  loading = signal(false);
  showPassword = false;

  constructor(private authService: AuthService, private router: Router) {}

  togglePassword() {
    this.showPassword = !this.showPassword;
  }

  onLogin() {
    // Username has no format restriction - only that something was actually typed.
    if (!this.username.trim()) {
      this.errorMessage.set('Please enter your username.');
      return;
    }
    if (this.password.length < 6) {
      this.errorMessage.set('Password must be at least 6 characters.');
      return;
    }

    this.errorMessage.set('');
    this.loading.set(true);

    // Only succeeds once this exact username/password matches a real account - either the
    // seeded admin account, or a resource person's account (auto-created when their
    // resource was added by the admin). There is no client-side-only login anymore.
    this.authService.login(this.username.trim(), this.password).subscribe({
      next: (res) => {
        this.loading.set(false);
        // Land admins on the Resource page and resource people on their Assigned Task
        // page - the sidebar (present on both) covers every other page from there.
        this.router.navigate([res.role === 'ADMIN' ? '/resources' : '/assigned-task']);
      },
      error: (err) => {
        this.loading.set(false);
        if (err.status === 401) {
          this.errorMessage.set('Invalid username or password.');
        } else {
          this.errorMessage.set('Could not reach the server. Please try again.');
        }
      }
    });
  }
}
