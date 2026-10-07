import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Pixkeys } from './pixkeys';

describe('Pixkeys', () => {
  let component: Pixkeys;
  let fixture: ComponentFixture<Pixkeys>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Pixkeys],
    }).compileComponents();

    fixture = TestBed.createComponent(Pixkeys);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
