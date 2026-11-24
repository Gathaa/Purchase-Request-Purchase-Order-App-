package Ninova.entity;

import jakarta.persistence.*;

// One row per year, holds the last PO number used that year
@Entity
@Table(name = "po_sequences")
public class PoSequence {

    @Id
    @Column(name = "year")
    private Integer seqYear;

    @Column(name = "`last_value`", nullable = false)
    private Integer lastValue;

    public Integer getSeqYear() { return seqYear; }
    public Integer getLastValue() { return lastValue; }
    public void setLastValue(Integer lastValue) { this.lastValue = lastValue; }
}