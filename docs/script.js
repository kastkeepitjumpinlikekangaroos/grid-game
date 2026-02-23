// Smooth scroll for nav anchor links
document.querySelectorAll('#nav a[href^="#"]').forEach(function (link) {
  link.addEventListener('click', function (e) {
    var target = document.querySelector(this.getAttribute('href'));
    if (target) {
      e.preventDefault();
      target.scrollIntoView({ behavior: 'smooth' });
    }
  });
});

// Sprite previews for character cards
(function () {
  var SPECIAL = { 'spaceman': 'character' };

  document.querySelectorAll('.char-card').forEach(function (card) {
    var h3 = card.querySelector('.char-header h3');
    if (!h3) return;

    var name = h3.textContent.toLowerCase().replace(/\s+/g, '');
    var file = SPECIAL[name] || name;

    // Create sprite element
    var sprite = document.createElement('div');
    sprite.className = 'char-sprite';
    sprite.style.backgroundImage = 'url(sprites/' + file + '.png)';

    // Wrap header + desc in char-info
    var info = document.createElement('div');
    info.className = 'char-info';
    var header = card.querySelector('.char-header');
    var desc = card.querySelector('.char-desc');
    info.appendChild(header);
    if (desc) info.appendChild(desc);

    // Create char-top container
    var top = document.createElement('div');
    top.className = 'char-top';
    top.appendChild(sprite);
    top.appendChild(info);

    // Insert at the beginning of the card
    card.insertBefore(top, card.firstChild);
  });
})();
