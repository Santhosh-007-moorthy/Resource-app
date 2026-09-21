import { CommonModule } from '@angular/common';
import { Component, OnInit, computed, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ResourceItem, ResourceService } from '../services/resource.service';
import { TaskItem, TaskService, isSundayDate } from '../services/task.service';

// Admin-only "Task Master Page": create/edit/delete a task's name, vehicle, weather
// regulations, the [fromDate, toDate] window it's allocated to one resource person, and
// any extra dates (on top of the always-blocked Sundays) the resource person can't report
// hours against. Rows can be filtered by Resource / Month (or "Current Month") / Work
// Status / Task via dropdowns, and sorted by From date ascending or descending. Completion
// is tracked separately from the allocated-period status so the Report Task / Assigned
// Task pages can show a task as Completed or still Pending.
@Component({
  selector: 'app-task-master',
  templateUrl: './task-master.component.html',
  styleUrls: ['./task-master.component.css'],
  imports: [CommonModule, FormsModule]
})
export class TaskMasterComponent implements OnInit {
  resources = signal<ResourceItem[]>([]);
  tasks = signal<TaskItem[]>([]);
  loadingTasks = signal(false);
  taskErrorMessage = signal('');

  // Canonical calendar order, used both to derive a task's month from its From date and
  // to keep the Month filter's dropdown options sorted Jan -> Dec rather than A-Z.
  months = [
    'January', 'February', 'March', 'April', 'May', 'June',
    'July', 'August', 'September', 'October', 'November', 'December'
  ];

  constructor(private resourceService: ResourceService, private taskService: TaskService) {}

  ngOnInit(): void {
    this.loadResources();
    this.loadTasks();
  }

  loadResources(): void {
    this.resourceService.getAllResources().subscribe({
      next: (data) => this.resources.set(data),
      error: (err) => console.error('Failed to load resources', err)
    });
  }

  private monthNameFromDate(dateText: string): string {
    const parsed = new Date(dateText + 'T00:00:00');
    return isNaN(parsed.getTime()) ? '' : this.months[parsed.getMonth()];
  }

  private currentMonthName(): string {
    return this.months[new Date().getMonth()];
  }

  // --- Column-header filters (replace the old free-text search) ---
  filterResourceNameId = signal('');
  // '' = All months, 'current' = this calendar month, otherwise one of `months` by name.
  filterMonth = signal('');
  filterStatus = signal<'' | 'pending' | 'completed'>('');
  filterTaskName = signal('');
  // Sort the (already-filtered) rows by From date; ascending = earliest first.
  sortOrder = signal<'asc' | 'desc'>('asc');

  onFilterResourceChange(nameId: string): void {
    this.filterResourceNameId.set(nameId);
  }

  onFilterMonthChange(month: string): void {
    this.filterMonth.set(month);
  }

  onFilterStatusChange(status: '' | 'pending' | 'completed'): void {
    this.filterStatus.set(status);
  }

  onFilterTaskNameChange(taskName: string): void {
    this.filterTaskName.set(taskName);
  }

  onSortOrderChange(order: 'asc' | 'desc'): void {
    this.sortOrder.set(order);
  }

  clearAllFilters(): void {
    this.filterResourceNameId.set('');
    this.filterMonth.set('');
    this.filterStatus.set('');
    this.filterTaskName.set('');
    this.sortOrder.set('asc');
  }

  hasActiveFilters = computed(
    () =>
      !!this.filterResourceNameId() ||
      !!this.filterMonth() ||
      !!this.filterStatus() ||
      !!this.filterTaskName() ||
      this.sortOrder() !== 'asc'
  );

  // Only resources that actually appear on a task, so the dropdown never lists someone
  // with nothing to filter down to. Sorted by display name.
  resourceFilterOptions = computed(() => {
    const map = new Map<string, string>();
    this.tasks().forEach((t) => {
      if (t.assignedResourceNameId) {
        map.set(t.assignedResourceNameId, t.assignedResourceName || t.assignedResourceNameId);
      }
    });
    return Array.from(map.entries())
      .sort((a, b) => a[1].localeCompare(b[1]))
      .map(([nameId, name]) => ({ nameId, name }));
  });

  // Filtered down to the months that actually occur, but always in Jan -> Dec order.
  monthFilterOptions = computed(() => {
    const present = new Set(this.tasks().map((t) => this.monthNameFromDate(t.fromDate)));
    return this.months.filter((m) => present.has(m));
  });

  taskNameFilterOptions = computed(() => {
    const names = this.tasks().map((t) => t.taskName).filter((n) => !!n);
    return Array.from(new Set(names)).sort();
  });

  filteredTasksForAdmin = computed(() => {
    const resourceNameId = this.filterResourceNameId();
    const month = this.filterMonth();
    const status = this.filterStatus();
    const taskName = this.filterTaskName();
    const order = this.sortOrder();
    const currentMonth = this.currentMonthName();

    const filtered = this.tasks().filter((t) => {
      if (resourceNameId && t.assignedResourceNameId !== resourceNameId) {
        return false;
      }
      if (month) {
        const wantedMonth = month === 'current' ? currentMonth : month;
        if (this.monthNameFromDate(t.fromDate) !== wantedMonth) {
          return false;
        }
      }
      if (status === 'pending' && t.completed) {
        return false;
      }
      if (status === 'completed' && !t.completed) {
        return false;
      }
      if (taskName && t.taskName !== taskName) {
        return false;
      }
      return true;
    });

    return [...filtered].sort((a, b) => {
      const cmp = a.fromDate.localeCompare(b.fromDate);
      return order === 'asc' ? cmp : -cmp;
    });
  });

  showTaskPopup = false;
  editingTaskId: number | null = null;
  newTaskName = '';
  newTaskCode = '';
  newVehicle = '';
  newWeatherRegulations = '';
  newFromDate = '';
  newToDate = '';
  newAssignedResourceId: number | null = null;
  newDisabledDates: string[] = [];
  newDisabledDateInput = '';
  disabledDateError = '';

  // While editing, the From date is locked (see openTaskPopup/saveTask) - only the To
  // date can change, so the allocated start of a task, once set, never shifts around.
  get isEditingExistingTask(): boolean {
    return this.editingTaskId !== null;
  }

  // The earliest date the From picker should allow when ADDING a new task - the 1st of
  // the current month. Not applied while editing: an existing task's From date is locked
  // and may legitimately already be in a previous month, so it must never be re-validated
  // against this rule just because the task is being edited.
  minTaskDate(): string {
    const now = new Date();
    const y = now.getFullYear();
    const m = String(now.getMonth() + 1).padStart(2, '0');
    return `${y}-${m}-01`;
  }

  loadTasks() {
    this.loadingTasks.set(true);
    this.taskService.getAllTasks().subscribe({
      next: (data) => {
        this.tasks.set(data);
        this.loadingTasks.set(false);
      },
      error: (err) => {
        console.error('Failed to load tasks', err);
        this.taskErrorMessage.set('Could not load tasks from the server.');
        this.loadingTasks.set(false);
      }
    });
  }

  openTaskPopup(task?: TaskItem) {
    this.taskErrorMessage.set('');
    this.disabledDateError = '';
    this.newDisabledDateInput = '';
    if (task) {
      this.editingTaskId = task.id ?? null;
      this.newTaskName = task.taskName;
      this.newTaskCode = task.taskCode;
      this.newVehicle = task.vehicle ?? '';
      this.newWeatherRegulations = task.weatherRegulations ?? '';
      this.newFromDate = task.fromDate;
      this.newToDate = task.toDate;
      this.newAssignedResourceId = task.assignedResourceId ?? null;
      this.newDisabledDates = [...(task.disabledDates ?? [])];
    } else {
      this.editingTaskId = null;
      this.newTaskName = '';
      this.newTaskCode = '';
      this.newVehicle = '';
      this.newWeatherRegulations = '';
      this.newFromDate = '';
      this.newToDate = '';
      this.newAssignedResourceId = null;
      this.newDisabledDates = [];
    }
    this.showTaskPopup = true;
  }

  // Sundays are always blocked for reporting (see isSundayDate) and never need to be
  // added here, so this only tracks the admin's extra manually-blocked dates.
  addDisabledDate(): void {
    this.disabledDateError = '';
    const date = this.newDisabledDateInput;
    if (!date) {
      return;
    }
    if (this.newFromDate && date < this.newFromDate || this.newToDate && date > this.newToDate) {
      this.disabledDateError = "The date must fall within the task's From/To range.";
      return;
    }
    if (isSundayDate(date)) {
      this.disabledDateError = 'Sundays are already disabled by default for every task.';
      return;
    }
    if (!this.newDisabledDates.includes(date)) {
      this.newDisabledDates = [...this.newDisabledDates, date].sort();
    }
    this.newDisabledDateInput = '';
  }

  removeDisabledDate(date: string): void {
    this.newDisabledDates = this.newDisabledDates.filter((d) => d !== date);
  }

  saveTask() {
    if (!this.newTaskName.trim() || !this.newTaskCode.trim() || !this.newFromDate || !this.newToDate) {
      this.taskErrorMessage.set('Please fill in Task Name, Task ID, From date, and To date.');
      return;
    }
    if (!this.newAssignedResourceId) {
      this.taskErrorMessage.set('Please select the resource person this task is allocated to.');
      return;
    }
    // Only enforced when adding a brand-new task. While editing, the From date is locked
    // (disabled in the form) and may already be in a previous month - that's expected and
    // must not block saving changes to the To date or other details.
    if (!this.isEditingExistingTask && this.newFromDate < this.minTaskDate()) {
      this.taskErrorMessage.set('The From date can\'t be in a previous month - the earliest allowed date is ' + this.minTaskDate() + '.');
      return;
    }
    if (this.newFromDate > this.newToDate) {
      this.taskErrorMessage.set('The From date must be on or before the To date.');
      return;
    }

    const assignedResource = this.resources().find((r) => r.id === this.newAssignedResourceId);

    const payload: TaskItem = {
      taskName: this.newTaskName.trim(),
      taskCode: this.newTaskCode.trim(),
      vehicle: this.newVehicle.trim(),
      weatherRegulations: this.newWeatherRegulations.trim(),
      fromDate: this.newFromDate,
      toDate: this.newToDate,
      disabledDates: this.newDisabledDates,
      assignedResourceId: this.newAssignedResourceId,
      assignedResourceNameId: assignedResource?.nameId ?? '',
      assignedResourceName: assignedResource?.name ?? ''
    };

    const request = this.editingTaskId
      ? this.taskService.updateTask(this.editingTaskId, payload)
      : this.taskService.addTask(payload);

    request.subscribe({
      next: () => {
        this.showTaskPopup = false;
        this.taskErrorMessage.set('');
        this.loadTasks();
      },
      error: (err) => {
        console.error('Failed to save task', err);
        if (err.status === 403) {
          this.taskErrorMessage.set('Only the admin account can modify the Task Master.');
        } else if (err.status === 409) {
          this.taskErrorMessage.set('That Task ID is already used by another task. Please use a unique ID.');
        } else {
          this.taskErrorMessage.set('Could not save task. Please try again.');
        }
      }
    });
  }

  // "Mark Complete" / "Mark Pending" - kept separate from the edit popup so flipping this
  // status is a single click and never risks touching a task's other details.
  toggleCompletion(task: TaskItem): void {
    if (task.id === undefined) {
      return;
    }
    const nextState = !task.completed;
    this.taskService.setTaskCompletion(task.id, nextState).subscribe({
      next: () => this.loadTasks(),
      error: (err) => {
        console.error('Failed to update completion status', err);
        if (err.status === 403) {
          this.taskErrorMessage.set('Only the admin account can modify the Task Master.');
        } else {
          this.taskErrorMessage.set('Could not update completion status. Please try again.');
        }
      }
    });
  }

  deleteTask(id?: number) {
    if (id === undefined) {
      return;
    }
    if (!confirm('Delete this task?')) {
      return;
    }
    this.taskService.deleteTask(id).subscribe({
      next: () => this.loadTasks(),
      error: (err) => {
        console.error('Failed to delete task', err);
        if (err.status === 403) {
          this.taskErrorMessage.set('Only the admin account can modify the Task Master.');
        }
      }
    });
  }

  cancelTaskPopup() {
    this.showTaskPopup = false;
    this.taskErrorMessage.set('');
  }
}
