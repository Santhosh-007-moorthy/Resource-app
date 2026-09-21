import { CommonModule } from '@angular/common';
import { Component, OnInit, computed, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AssignmentItem, AssignmentService } from '../services/assignment.service';
import { TaskItem, TaskService, isSundayDate } from '../services/task.service';
import { AuthService } from '../services/auth.service';

// Resource-person-only "Report Task" page: log hours against one of your own assigned
// tasks, and view/download/delete your own saved entries. The resource identity comes
// straight from the login session (see AuthService) - there's no manual resource picker
// anymore, since a resource person's username IS their resource's Name ID.
@Component({
  selector: 'app-report-task',
  templateUrl: './report-task.component.html',
  styleUrls: ['./report-task.component.css'],
  imports: [CommonModule, FormsModule]
})
export class ReportTaskComponent implements OnInit {
  assignments = signal<AssignmentItem[]>([]);
  loading = signal(false);
  errorMessage = signal('');

  months = [
    'January', 'February', 'March', 'April', 'May', 'June',
    'July', 'August', 'September', 'October', 'November', 'December'
  ];

  tasks = signal<TaskItem[]>([]);

  constructor(
    private assignmentService: AssignmentService,
    private taskService: TaskService,
    private authService: AuthService
  ) {}

  get resourceId(): number | null {
    return this.authService.getResourceId();
  }

  get resourceName(): string {
    return this.authService.getResourceName();
  }

  get resourceNameId(): string {
    return this.authService.getResourceNameId();
  }

  // Only the tasks allocated to this signed-in resource person.
  myTasks = computed(() => {
    const myId = this.resourceId;
    return this.tasks().filter((t) => t.assignedResourceId === myId);
  });

  taskByCode = computed(() => {
    const map = new Map<string, TaskItem>();
    this.tasks().forEach((t) => map.set(t.taskCode, t));
    return map;
  });

  selectedTask(): TaskItem | null {
    return this.taskByCode().get(this.selectedTaskId) ?? null;
  }

  // Whether the selected task's allocated [fromDate, toDate] window currently covers
  // today - independent of completion, so the two "closed" reasons can be told apart
  // in the UI (see the template hints).
  selectedTaskIsWithinPeriod(): boolean {
    return this.selectedTask()?.withinAllocatedPeriod ?? false;
  }

  // A task is open for new entries only while it's within its allocated period AND the
  // admin hasn't marked it Completed. Once the admin marks it Completed, it's disabled
  // here even if the allocated period technically hasn't ended yet.
  selectedTaskIsOpen(): boolean {
    return this.selectedTaskIsWithinPeriod() && !this.selectedTaskIsCompleted();
  }

  selectedTaskIsCompleted(): boolean {
    return this.selectedTask()?.completed ?? false;
  }

  // Looks up a task's admin-set Completed/Pending status by its Task ID, so the saved
  // tasks table can show it per row without duplicating that flag onto every Assignment.
  isTaskCompleted(taskCode: string): boolean {
    return this.taskByCode().get(taskCode)?.completed ?? false;
  }

  selectedTaskDateRange(): { from: Date; to: Date } | null {
    const task = this.selectedTask();
    if (!task || !task.fromDate || !task.toDate) {
      return null;
    }
    return { from: new Date(task.fromDate + 'T00:00:00'), to: new Date(task.toDate + 'T00:00:00') };
  }

  allowedYears(): number[] {
    const range = this.selectedTaskDateRange();
    if (!range) {
      return [];
    }
    const years: number[] = [];
    for (let y = range.from.getFullYear(); y <= range.to.getFullYear(); y++) {
      years.push(y);
    }
    return years;
  }

  allowedMonthsForSelectedYear(): string[] {
    const range = this.selectedTaskDateRange();
    if (!range || !this.selectedYear) {
      return [];
    }
    const year = this.selectedYear;
    return this.months.filter((_, idx) => {
      const monthStart = new Date(year, idx, 1);
      const monthEnd = new Date(year, idx + 1, 0);
      return monthEnd >= range.from && monthStart <= range.to;
    });
  }

  // Same "open" rule as selectedTaskIsOpen(), but looked up by task code so it can be
  // applied to any row in the saved-tasks table, not just the currently selected task.
  isTaskOpenForEntry(taskCode: string): boolean {
    const task = this.taskByCode().get(taskCode);
    return !!task?.withinAllocatedPeriod && !task?.completed;
  }

  // Deleting is only allowed for entries dated in the current or previous calendar month.
  isWithinDeletableWindow(dateText: string | undefined): boolean {
    if (!dateText) {
      return false;
    }
    const entryDate = new Date(dateText + 'T00:00:00');
    if (isNaN(entryDate.getTime())) {
      return false;
    }
    const now = new Date();
    const previousMonthStart = new Date(now.getFullYear(), now.getMonth() - 1, 1);
    const nextMonthStart = new Date(now.getFullYear(), now.getMonth() + 1, 1);
    return entryDate >= previousMonthStart && entryDate < nextMonthStart;
  }

  canDeleteAssignment(assignment: AssignmentItem): boolean {
    return this.isTaskOpenForEntry(assignment.taskCode) && this.isWithinDeletableWindow(assignment.date);
  }

  // --- Table filters ---
  filterMonth = signal('');
  filterTask = signal('');
  filterYear = signal<number | null>(null);
  years: number[] = [];

  taskFilterOptions = computed(() => {
    const names = this.assignments().map((a) => a.taskName).filter((n) => !!n);
    return Array.from(new Set(names)).sort();
  });

  filteredAssignments = computed(() => {
    let list = this.assignments();
    if (this.filterMonth()) {
      list = list.filter((a) => a.month === this.filterMonth());
    }
    if (this.filterTask()) {
      list = list.filter((a) => a.taskName === this.filterTask());
    }
    if (this.filterYear() !== null) {
      list = list.filter((a) => a.year === this.filterYear());
    }
    return list;
  });

  onFilterMonthChange(month: string): void {
    this.filterMonth.set(month);
    this.currentPage.set(1);
  }

  onFilterTaskChange(taskName: string): void {
    this.filterTask.set(taskName);
    this.currentPage.set(1);
  }

  onFilterYearChange(year: number | null): void {
    this.filterYear.set(year);
    this.currentPage.set(1);
  }

  clearTableFilters(): void {
    this.filterMonth.set('');
    this.filterTask.set('');
    this.filterYear.set(null);
    this.currentPage.set(1);
  }

  private escapeCsv(value: unknown): string {
    const str = value === null || value === undefined ? '' : String(value);
    if (/[",\n]/.test(str)) {
      return `"${str.replace(/"/g, '""')}"`;
    }
    return str;
  }

  private csvHeaders = ['S.No', 'Resource ID', 'Resource', 'Task', 'Task ID', 'Date', 'Month', 'Year', 'Hours', 'Remarks'];

  private toCsvRow(a: AssignmentItem, i: number): string {
    return [
      i + 1,
      a.resourceNameId,
      a.resourceName,
      a.taskName,
      a.taskCode,
      a.date,
      a.month,
      a.year,
      a.hours,
      a.remarks
    ].map((v) => this.escapeCsv(v)).join(',');
  }

  private triggerCsvDownload(fileName: string, lines: string[]): void {
    const csvContent = lines.join('\n');
    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);

    const link = document.createElement('a');
    link.href = url;
    link.download = fileName;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
  }

  downloadCsv(): void {
    const rows = this.filteredAssignments();
    if (rows.length === 0) {
      return;
    }
    const lines = [this.csvHeaders.join(',')];
    rows.forEach((a, i) => lines.push(this.toCsvRow(a, i)));

    const nameSlug = (this.resourceName || 'resource').replace(/\s+/g, '_');
    const filterSlug = [this.filterMonth(), this.filterTask(), this.filterYear()]
      .filter((v) => v !== '' && v !== null)
      .join('_');
    const fileName = `saved_tasks_${nameSlug}${filterSlug ? '_' + filterSlug : ''}.csv`;

    this.triggerCsvDownload(fileName, lines);
  }

  // --- Pagination ---
  pageSize = 10;
  currentPage = signal(1);

  totalPages = computed(() => Math.max(1, Math.ceil(this.filteredAssignments().length / this.pageSize)));

  pagedAssignments = computed(() => {
    const start = (this.currentPage() - 1) * this.pageSize;
    return this.filteredAssignments().slice(start, start + this.pageSize);
  });

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

  // --- Entry form state ---
  selectedTaskName = '';
  selectedTaskId = '';
  selectedMonth = '';
  selectedYear: number | null = null;
  selectedDate = '';
  workedHours: number = 1;
  remarks: string = '';
  calendarDays: number[] = [];

  // Total hours already logged per date, across ALL of this resource's tasks - derived
  // from their real saved assignments (not a local running total) so it survives a page
  // reload and stays correct no matter which task's calendar you're looking at. This is
  // what decides whether a day is "Full" (8/8 hrs, shown in blue and unselectable) and
  // how many hours remain when it isn't.
  hoursByDate = computed(() => {
    const map: Record<string, number> = {};
    this.assignments().forEach((a) => {
      const date = a.date;
      if (!date) {
        return;
      }
      map[date] = (map[date] || 0) + (a.hours || 0);
    });
    return map;
  });

  // Per-day info the calendar template reads to decide each cell's label and state:
  // - "blocked": Sunday or one of the admin's manually-disabled dates for this task -
  //   shown greyed out and unselectable, but still visible in the grid.
  // - "full": 8/8 hours already logged for that date (any task) - shown in blue and
  //   unselectable, but still visible.
  // - otherwise open, showing how many of the 8 hours remain.
  dayInfo(day: number): { hoursUsed: number; hoursRemaining: number; isFull: boolean; isBlocked: boolean; blockedReason: string } {
    const dateStr = this.buildDateString(day);
    const hoursUsed = this.hoursByDate()[dateStr] || 0;
    const task = this.selectedTask();
    const isSunday = isSundayDate(dateStr);
    const isAdminDisabled = !!task?.disabledDates?.includes(dateStr);
    return {
      hoursUsed,
      hoursRemaining: Math.max(0, 8 - hoursUsed),
      isFull: hoursUsed >= 8,
      isBlocked: isSunday || isAdminDisabled,
      blockedReason: isSunday ? 'Sunday' : (isAdminDisabled ? 'Disabled by admin' : '')
    };
  }

  incrementHours(): void {
    if (this.workedHours < 8) {
      this.workedHours += 1;
    }
  }

  decrementHours(): void {
    if (this.workedHours > 1) {
      this.workedHours -= 1;
    }
  }

  ngOnInit(): void {
    this.loadAll();
  }

  loadAll(): void {
    this.loading.set(true);

    this.taskService.getAllTasks().subscribe({
      next: (data) => this.tasks.set(data),
      error: (err) => console.error('Failed to load tasks', err)
    });

    this.loadMyAssignments();
  }

  loadMyAssignments(): void {
    const myId = this.resourceId;
    if (myId === null) {
      this.assignments.set([]);
      this.loading.set(false);
      return;
    }

    this.loading.set(true);
    this.assignmentService.getAssignmentsByResource(myId).subscribe({
      next: (data) => {
        this.assignments.set(data);
        this.filterMonth.set('');
        this.filterTask.set('');
        this.filterYear.set(null);
        this.currentPage.set(1);
        this.loading.set(false);

        const currentYear = new Date().getFullYear();
        this.years = [];
        for (let y = currentYear - 5; y <= currentYear + 5; y++) {
          this.years.push(y);
        }
      },
      error: (err) => {
        console.error('Failed to load assignments', err);
        this.errorMessage.set('Could not load your saved tasks from the server.');
        this.loading.set(false);
      }
    });
  }

  onTaskSelected(taskName: string): void {
    const selectedTask = this.myTasks().find((task) => task.taskName === taskName);
    this.selectedTaskId = selectedTask?.taskCode ?? '';
    this.selectedMonth = '';
    this.selectedYear = null;
    this.selectedDate = '';
    this.calendarDays = [];
  }

  onYearSelected(): void {
    this.selectedMonth = '';
    this.selectedDate = '';
    this.calendarDays = [];
  }

  updateCalendarRange() {
    const monthIndex = this.months.indexOf(this.selectedMonth);
    if (monthIndex === -1 || !this.selectedYear) {
      this.calendarDays = [];
      return;
    }

    const range = this.selectedTaskDateRange();
    const daysInMonth = new Date(this.selectedYear, monthIndex + 1, 0).getDate();
    const allDays = Array.from({ length: daysInMonth }, (_, i) => i + 1);

    // Only days outside the task's allocated [from, to] window are left out entirely.
    // Sundays, admin-disabled dates, and already-full (8/8 hrs) days are still shown -
    // just greyed out / blued out and unselectable (see dayInfo() and the template).
    this.calendarDays = allDays.filter((d) => {
      if (!range) {
        return true;
      }
      const dateObj = new Date(this.selectedYear as number, monthIndex, d);
      return dateObj >= range.from && dateObj <= range.to;
    });

    const firstOpenDay = this.calendarDays.find((d) => {
      const info = this.dayInfo(d);
      return !info.isBlocked && !info.isFull;
    });
    this.selectedDate = firstOpenDay ? this.buildDateString(firstOpenDay) : '';
  }

  selectDay(day: number): void {
    const info = this.dayInfo(day);
    if (info.isBlocked || info.isFull) {
      return;
    }
    this.selectedDate = this.buildDateString(day);
  }

  isSelectedDay(day: number): boolean {
    return this.selectedDate === this.buildDateString(day);
  }

  private buildDateString(day: number): string {
    const monthIndex = this.months.indexOf(this.selectedMonth);
    const mm = String(monthIndex + 1).padStart(2, '0');
    const dd = String(day).padStart(2, '0');
    return `${this.selectedYear}-${mm}-${dd}`;
  }

  saveTask() {
    if (this.resourceId === null) {
      this.errorMessage.set('Your account is not linked to a resource. Please contact the admin.');
      return;
    }

    if (!this.selectedTaskName || !this.selectedTaskId || !this.selectedDate || !this.workedHours) {
      this.errorMessage.set('Please select Task, Date, and Hours.');
      return;
    }

    if (!this.selectedTaskIsOpen()) {
      this.errorMessage.set(
        this.selectedTaskIsCompleted()
          ? "This task has been marked Completed by the admin, so a new entry can't be added for it."
          : "This task's allocated period is closed, so a new entry can't be added for it."
      );
      return;
    }

    if (isSundayDate(this.selectedDate) || (this.selectedTask()?.disabledDates ?? []).includes(this.selectedDate)) {
      this.errorMessage.set('This date is not open for reporting on this task. Please pick another date.');
      return;
    }

    const currentTotal = this.hoursByDate()[this.selectedDate] || 0;
    if (currentTotal + this.workedHours > 8) {
      this.errorMessage.set('Total hours cannot exceed 8 for a single day.');
      return;
    }

    const payload: AssignmentItem = {
      resourceNameId: this.resourceNameId,
      resourceName: this.resourceName,
      taskName: this.selectedTaskName,
      taskCode: this.selectedTaskId,
      date: this.selectedDate,
      month: this.selectedMonth,
      year: this.selectedYear!,
      hours: this.workedHours,
      remarks: this.remarks
    };

    this.assignmentService.addAssignment(this.resourceId, payload).subscribe({
      next: () => {
        this.loadMyAssignments();
        this.errorMessage.set('');
        this.remarks = '';
      },
      error: (err) => {
        console.error('Failed to save assignment', err);
        if (err.status === 403 && err.error?.message) {
          this.errorMessage.set(err.error.message);
        } else {
          this.errorMessage.set('Could not save assignment. Please try again.');
        }
      }
    });
  }

  deleteAssignment(assignment: AssignmentItem): void {
    if (assignment.id === undefined || this.resourceId === null) {
      return;
    }
    if (!this.isTaskOpenForEntry(assignment.taskCode)) {
      this.errorMessage.set(
        this.isTaskCompleted(assignment.taskCode)
          ? 'This task has been marked Completed by the admin, so this entry can no longer be deleted.'
          : "This task's allocated period is closed, so this entry can no longer be deleted."
      );
      return;
    }
    if (!this.isWithinDeletableWindow(assignment.date)) {
      this.errorMessage.set('Only entries from the current month or the previous month can be deleted.');
      return;
    }

    const id = assignment.id;
    this.assignmentService.deleteAssignment(this.resourceId, id).subscribe({
      next: () => {
        this.assignments.set(this.assignments().filter((a) => a.id !== id));
        if (this.currentPage() > this.totalPages()) {
          this.currentPage.set(this.totalPages());
        }
      },
      error: (err) => {
        console.error('Failed to delete assignment', err);
        if (err.status === 403 && err.error?.message) {
          this.errorMessage.set(err.error.message);
        } else {
          this.errorMessage.set('Could not delete assignment. Please try again.');
        }
      }
    });
  }
}
