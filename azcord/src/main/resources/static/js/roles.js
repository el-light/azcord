// roles.js – dedicated to server role CRUD
(() => {
    const API = API_BASE_URL;   // already declared globally
    let roles = []; // currentServer will be assigned in openRolesManagerBtn.onclick
  
    /* ---------- helpers ---------- */
    function randColor() {
      const h = Math.floor(Math.random()*360);
      return `#${h.toString(16).padStart(2,'0')}80c0`.slice(0,7);
    }
    function badge(role){
      return `<span class="role-badge" style="background:${role.colorHex}">${role.name}</span>`;
    }
  
    /* ---------- UI wiring ---------- */
    const openRolesManagerBtn = document.getElementById('openRolesManagerBtn');
    const rolesModal = document.getElementById('rolesModal');
    const closeRolesModalBtn = document.getElementById('closeRolesModalBtn');
    const roleNameInput = document.getElementById('roleNameInput');
    const roleColorInput = document.getElementById('roleColorInput');
    const createRoleBtn = document.getElementById('createRoleBtn');
    const rolesList = document.getElementById('rolesList');
  
    if (openRolesManagerBtn) {
    openRolesManagerBtn.onclick = async () => {
        if (!window.currentServerId) {
          showToast("Select a server first.", "error"); // Ensure showToast is globally available
          return;
        }
        currentServer = window.currentServerId;          // grab from global
        if (rolesModal) rolesModal.classList.remove('hidden');
      await fetchRoles();
    };
    }
    closeRolesModalBtn.onclick = () => rolesModal.classList.add('hidden');
  
    createRoleBtn.onclick = async () => {
        const name  = roleNameInput.value.trim();
        const color = roleColorInput.value.trim();
      
        // sanity checks
        if (!name) {
          showToast("Role name is required", "error");
          return;
        }
        if (!color) {
          showToast("Role color is required", "error");
          return;
        }
      
        // Backend DTO has a field named `color_hex` (with underscore), not `colorHex`
        const payload = {
          name,
          color_hex: color,
          permissions: []    // or whatever default perms you want
        };
      
        try {
          const res = await api(`${API}/servers/${currentServer}/roles`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
      });
      
          if (res.status === 400) {
            // Pull the validation errors out of the JSON response
            const err = await res.json();
            const msgs = err.errors?.map(e => e.defaultMessage).join(", ") || err.message;
            showToast("Validation error: " + msgs, "error");
            return;
          }
          if (res.status === 403) {
            showToast("You don't have permission to create roles here.", "error");
            rolesModal.classList.add('hidden');
            return;
          }
          if (!res.ok) throw new Error(await res.text());
      
          // success!
          await fetchRoles();
          roleNameInput.value = '';
          roleColorInput.value = '#808080';
        } catch (e) {
          console.error(e);
          showToast("Could not create role: " + e.message, "error");
        }
    };
         
      
  
    const defaultCreate = createRoleBtn.onclick; // Save the original create function
  
    async function fetchRoles(){
      if (!currentServer) return;
      const res = await api(`${API}/servers/${currentServer}/roles`, {headers:auth()}); // Use api()
      if (res.status === 403){
             showToast("You don't have permission to manage roles on this server.","error");
             rolesModal.classList.add('hidden');
             return;
          }
          if (!res.ok) throw new Error(await res.text());
      roles = await res.json();
      renderRoles();
    }
  
    function renderRoles(){
      rolesList.innerHTML = roles.map(r =>
          `<li data-id="${r.id}" class="flex items-center gap-2">
             ${badge(r)}
             <button class="btn text-xs ml-auto edit">✏️</button>
             <button class="btn-danger text-xs delete">🗑️</button>
           </li>`).join('');
    }
  
    // Replace existing rolesList.onclick with addEventListener
    rolesList.addEventListener('click', async e => {
      const assignButton = e.target.closest('[data-assign]');
      const editButton = e.target.closest('.edit'); // Check for edit button
      const deleteButton = e.target.closest('.delete'); // Check for delete button

      if (assignButton) {
        const roleId = assignButton.getAttribute('role-id');
        const userId = prompt('User-ID to give this role to:');
        if (!userId || isNaN(parseInt(userId))) { // Basic validation for userId
            if (userId !== null) showToast('Invalid User ID provided.', 'error');
            return;
        }
        try {
          const response = await api(`${API}/servers/${currentServer}/roles/${roleId}/members/${userId}`, {
              method: 'POST'
          });
          if (!response.ok) {
            let errorText = 'Failed to assign role';
            try { errorText = await response.text(); } catch (err) { /* ignore */ }
            throw new Error(`Assign role failed: ${response.status} ${errorText}`);
          }
          showToast('Role added ✅', 'success');
        } catch (error) {
          console.error('Error assigning role:', error);
          showToast(error.message, 'error');
        }
        return; 
      }

      const li = e.target.closest('li[data-id]');
      if (!li) return;
      const id = li.dataset.id;

      if (deleteButton) {
         try {
            const response = await api(`${API}/servers/${currentServer}/roles/${id}`, {method:'DELETE', headers:auth()});
            if (!response.ok) {
                let errorText = 'Failed to delete role';
                try { errorText = await response.text(); } catch (err) { /* ignore */ }
                throw new Error(`Delete role failed: ${response.status} ${errorText}`);
            }
            fetchRoles(); 
         } catch (error) {
            console.error('Error deleting role:', error);
            showToast(error.message, 'error');
         }
         return;
      }
      
      if (editButton) {
        const role = roles.find(r=>r.id==id);
        if (!role) return;
        roleNameInput.value = role.name;
        roleColorInput.value = role.colorHex;
        createRoleBtn.textContent='Save';
        createRoleBtn.onclick = async () => {
           const upd = { name: roleNameInput.value, colorHex: roleColorInput.value };
           try {
                const response = await api(`${API}/servers/${currentServer}/roles/${id}`, {
               method:'PUT', headers:authJson(), body:JSON.stringify(upd)
           });
                if (!response.ok) {
                    let errorText = 'Failed to update role';
                    try { errorText = await response.text(); } catch (err) { /* ignore */ }
                    throw new Error(`Update role failed: ${response.status} ${errorText}`);
                }
           createRoleBtn.textContent='Add';
           createRoleBtn.onclick = defaultCreate;
           roleNameInput.value=''; fetchRoles();
           } catch (error) {
                console.error('Error updating role:', error);
                showToast(error.message, 'error');
           }
        };
      }
    });
  
    // helper for headers
    function auth(){ return {}; } // api() adds Authorization
    function authJson(){ return { 'Content-Type':'application/json' }; } // api() adds Authorization
  })();
  