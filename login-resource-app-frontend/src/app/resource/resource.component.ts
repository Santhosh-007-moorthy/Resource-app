import { CommonModule } from '@angular/common';
import { Component, OnInit, computed, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ResourceItem, ResourceService } from '../services/resource.service';

// Admin-only "Resource Page": add/view/delete Resource Persons. Adding a resource here also
// auto-creates that person's login account on the backend (username = Name ID, one shared
// password for everyone) - see ResourceController on the server side.
@Component({
  selector: 'app-resource',
  templateUrl: './resource.component.html',
  styleUrls: ['./resource.component.css'],
  imports: [CommonModule, FormsModule]
})
export class ResourceComponent implements OnInit {
  resources = signal<ResourceItem[]>([]);
  loading = signal(false);
  errorMessage = signal('');

  showPopup = false;
  newNameId = '';
  newName = '';
  newRole = '';

  constructor(private resourceService: ResourceService) {}

  sharedPassword = signal<string | null>(null);

  ngOnInit(): void {
    this.loadResources();
    this.resourceService.getSharedLoginPassword().subscribe({
      next: (res) => this.sharedPassword.set(res.password),
      error: (err) => console.error('Failed to load shared login password', err)
    });
  }

  // --- Role filter ---
  selectedRole = signal<string>('');

  roleOptions = computed(() => {
    const roles = this.resources().map((r) => r.role).filter((r) => !!r);
    return Array.from(new Set(roles)).sort();
  });

  filteredResources = computed(() => {
    const role = this.selectedRole();
    const all = this.resources();
    return role ? all.filter((r) => r.role === role) : all;
  });

  // --- Pagination: show 10 at a time with Previous/Next controls ---
  pageSize = 10;
  currentPage = signal(1);

  totalPages = computed(() => Math.max(1, Math.ceil(this.filteredResources().length / this.pageSize)));

  pagedResources = computed(() => {
    const start = (this.currentPage() - 1) * this.pageSize;
    return this.filteredResources().slice(start, start + this.pageSize);
  });

  loadResources() {
    this.loading.set(true);
    this.resourceService.getAllResources().subscribe({
      next: (data) => {
        this.resources.set(data);
        this.currentPage.set(1);
        this.loading.set(false);
      },
      error: (err) => {
        console.error('Failed to load resources', err);
        this.errorMessage.set('Could not load resources from the server.');
        this.loading.set(false);
      }
    });
  }

  onRoleFilterChange(role: string): void {
    this.selectedRole.set(role);
    this.currentPage.set(1);
  }

  clearRoleFilter(): void {
    this.onRoleFilterChange('');
  }

  nextPage(): void {
    if (this.currentPage() < this.totalPages()) {
      this.currentPage.update((p) => p + 1);
    }
  }

  prevPage(): void {
    if (this.currentPage() > 1) {
      this.currentPage.update((p) => p - 1);
    }
  }

  openPopup() {
    this.showPopup = true;
    this.errorMessage.set('');
  }

  saveResource() {
    if (this.newNameId.trim() && this.newName.trim() && this.newRole.trim()) {
      const payload: ResourceItem = {
        nameId: this.newNameId.trim(),
        name: this.newName.trim(),
        role: this.newRole.trim()
      };

      this.resourceService.addResource(payload).subscribe({
        next: () => {
          this.newNameId = '';
          this.newName = '';
          this.newRole = '';
          this.showPopup = false;
          this.errorMessage.set('');
          this.loadResources();
        },
        error: (err) => {
          console.error('Failed to save resource', err);
          if (err.status === 409) {
            this.errorMessage.set('That Name ID is already used by another resource or login account. Please use a unique ID.');
          } else {
            this.errorMessage.set('Could not save resource. Please try again.');
          }
        }
      });
    } else {
      this.errorMessage.set('Please enter Name ID, Name, and Role.');
    }
  }

  deleteResource(id?: number) {
    if (id === undefined) {
      return;
    }
    if (!confirm('Delete this resource? Their login account will also be removed.')) {
      return;
    }
    this.resourceService.deleteResource(id).subscribe({
      next: () => this.loadResources(),
      error: (err) => console.error('Failed to delete resource', err)
    });
  }

  cancel() {
    this.showPopup = false;
    this.errorMessage.set('');
  }
}
