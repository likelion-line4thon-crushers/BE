package line4thon.boini.presenter.pdf.dto;

import line4thon.boini.presenter.pdf.model.FontStatus;

public record FontEntry(String name, FontStatus status, boolean embedded, boolean installed) {}
