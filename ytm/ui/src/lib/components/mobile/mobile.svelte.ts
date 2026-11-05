/** Jam for Android: phone-shell state shared by the bottom tabs, More sheet and player. */
export const mobile = $state({
	/** The More sheet (history, stats, Listen Together, settings …). */
	moreOpen: false,
	/** The full-screen player's lower sheet: closed, or which tab it shows. */
	sheet: null as null | 'queue' | 'lyrics' | 'comments'
});
