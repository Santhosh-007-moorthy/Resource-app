import { CommonModule } from '@angular/common';
import { Component, OnInit, computed, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { catchError, forkJoin, of } from 'rxjs';
import { ResourceItem, ResourceService } from '../services/resource.service';
import { AssignmentItem, AssignmentService } from '../services/assignment.service';

// Admin-only "Records Page": every resource person's saved task entries in one place, with
// Resource/Month/Task/Year filters and CSV export (filtered, or every resource at once).
@Component({
  selector: 'app-records',
  templateUrl: './records.component.html',
  styleUrls: ['./records.component.css'],
  imports: [CommonModule, FormsModule]
})
export class RecordsComponent implements OnInit {
  resources = signal<ResourceItem[]>([]);
  allAssignments = signal<AssignmentItem[]>([]);
  loading = signal(false);
  errorMessage = signal('');

  months = [
    'January', 'February', 'March', 'April', 'May', 'June',
    'July', 'August', 'September', 'October', 'November', 'December'
  ];
  years: number[] = [];

  constructor(private resourceService: ResourceService, private assignmentService: AssignmentService) {
    const currentYear = new Date().getFullYear();
    for (let y = currentYear - 5; y <= currentYear + 5; y++) {
      this.years.push(y);
    }
  }

  ngOnInit(): void {
    this.loadAll();
  }

  loadAll(): void {
    this.loading.set(true);
    this.resourceService.getAllResources().subscribe({
      next: (data) => {
        this.resources.set(data);
        this.loadAllAssignments();
      },
      error: (err) => {
        console.error('Failed to load resources', err);
        this.errorMessage.set('Could not load resources from the server.');
        this.loading.set(false);
      }
    });
  }

  private loadAllAssignments(): void {
    const allResources = this.resources();
    if (allResources.length === 0) {
      this.allAssignments.set([]);
      this.loading.set(false);
      return;
    }

    // Each resource's assignments are fetched independently and errors are caught per-request
    // (falling back to an empty list) so one resource person with a broken/unreachable
    // database can't blank out everyone else's records on this page.
    const requests = allResources
      .filter((r) => r.id !== undefined)
      .map((r) =>
        this.assignmentService.getAssignmentsByResource(r.id as number).pipe(
          catchError((err) => {
            console.error(`Failed to load records for resource ${r.nameId} (id ${r.id})`, err);
            return of([] as AssignmentItem[]);
          })
        )
      );

    forkJoin(requests).subscribe({
      next: (results) => {
        this.allAssignments.set(results.flat());
        this.loading.set(false);
      },
      error: (err) => {
        console.error('Failed to load records', err);
        this.errorMessage.set('Could not load resource records from the server.');
        this.loading.set(false);
      }
    });
  }

  // --- Filters ---
  resourceFilter = signal<string>(''); // a Resource Name ID, '' = all resources
  filterMonth = signal('');
  filterTask = signal('');
  filterYear = signal<number | null>(null);

  onResourceFilterChange(nameId: string): void {
    this.resourceFilter.set(nameId);
    this.currentPage.set(1);
  }

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

  clearFilters(): void {
    this.resourceFilter.set('');
    this.filterMonth.set('');
    this.filterTask.set('');
    this.filterYear.set(null);
    this.currentPage.set(1);
  }

  taskFilterOptions = computed(() => {
    const names = this.allAssignments().map((a) => a.taskName).filter((n) => !!n);
    return Array.from(new Set(names)).sort();
  });

  filteredAssignments = computed(() => {
    let list = this.allAssignments();
    if (this.resourceFilter()) {
      list = list.filter((a) => a.resourceNameId === this.resourceFilter());
    }
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

  // --- CSV export ---
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

    const nameSlug = (this.resourceFilter() || 'all_resources').replace(/\s+/g, '_');
    const filterSlug = [this.filterMonth(), this.filterTask(), this.filterYear()]
      .filter((v) => v !== '' && v !== null)
      .join('_');
    const fileName = `saved_tasks_${nameSlug}${filterSlug ? '_' + filterSlug : ''}.csv`;

    this.triggerCsvDownload(fileName, lines);
  }

  downloadAllResourcesCsv(): void {
    const allRows = this.allAssignments();
    if (allRows.length === 0) {
      return;
    }
    const lines = [this.csvHeaders.join(',')];
    allRows.forEach((a, i) => lines.push(this.toCsvRow(a, i)));
    this.triggerCsvDownload('all_resources_saved_tasks.csv', lines);
  }
}
