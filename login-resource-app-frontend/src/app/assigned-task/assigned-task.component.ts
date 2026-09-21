import { CommonModule } from '@angular/common';
import { Component, OnInit, computed, signal } from '@angular/core';
import { TaskItem, TaskService } from '../services/task.service';
import { AuthService } from '../services/auth.service';

// Resource-person-only "Assigned Task" page: a read-only look at every task the admin has
// allocated to this signed-in resource, resolved straight from their login session - no
// manual "which resource are you" picker needed anymore.
@Component({
  selector: 'app-assigned-task',
  templateUrl: './assigned-task.component.html',
  styleUrls: ['./assigned-task.component.css'],
  imports: [CommonModule]
})
export class AssignedTaskComponent implements OnInit {
  loading = signal(false);
  errorMessage = signal('');
  private allTasks = signal<TaskItem[]>([]);

  constructor(private taskService: TaskService, private authService: AuthService) {}

  get resourceName(): string {
    return this.authService.getResourceName();
  }

  myTasks = computed(() => {
    const myId = this.authService.getResourceId();
    return this.allTasks().filter((t) => t.assignedResourceId === myId);
  });

  ngOnInit(): void {
    this.loading.set(true);
    this.taskService.getAllTasks().subscribe({
      next: (data) => {
        this.allTasks.set(data);
        this.loading.set(false);
      },
      error: (err) => {
        console.error('Failed to load tasks', err);
        this.errorMessage.set('Could not load your assigned tasks from the server.');
        this.loading.set(false);
      }
    });
  }
}
